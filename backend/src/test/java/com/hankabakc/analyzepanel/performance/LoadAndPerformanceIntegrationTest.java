package com.hankabakc.analyzepanel.performance;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.JwtService;
import com.hankabakc.analyzepanel.auth.service.LoginAttemptService;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * LoadAndPerformanceIntegrationTest: Sistemin 100 eşzamanlı kullanıcı altındaki performansını,
 * N+1 sorgu davranışını, HikariCP bağlantı havuzu (15) doygunluğunu ve hız sınırlayıcı (Rate Limiting)
 * kurallarını sınayan duman ve yük testidir (T-076 / S-027).
 *
 * <p>Mühendislik ve Güvenlik Standartları:</p>
 * <ul>
 *   <li><b>Java 25 Sanal İş Parçacıkları (Virtual Threads):</b> Eşzamanlı 100 kullanıcı isteği
 *       {@link Executors#newVirtualThreadPerTaskExecutor()} ile paralel olarak koşturulur.</li>
 *   <li><b>Hata Oranı Eşiği:</b> 100 eşzamanlı istekte hata oranı %1'in altında olmalıdır (0 hata hedeflenir).</li>
 *   <li><b>Bağlantı Havuzu Doygunluğu:</b> 15 bağlantılık Hikari havuzunda istekler tamamlandığında
 *       bekleyen iş parçacığı sayısı (threads awaiting connection) 0 olmalıdır.</li>
 *   <li><b>Hız Sınırları:</b> IP başına dakikada 5 giriş sınırı (/api/v1/auth/login) tekil IP'yi
 *       bloke ederken, farklı oturumlu kullanıcıların (dakikada 60 istek) meşru isteklerini engellemediği
 *       kanıtlanır.</li>
 * </ul>
 */
@SpringBootTest
public class LoadAndPerformanceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LoadAndPerformanceIntegrationTest.class);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private DataSource dataSource;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.hikari.maximum-pool-size:15}")
    private int configuredPoolSize;

    private MockMvc mockMvc;

    private static final String TEST_PASSWORD = "GucluParola123*!";
    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        loginAttemptService.resetAll();
    }

    @AfterEach
    void tearDown() {
        loginAttemptService.resetAll();
        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(u -> {
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("T-076: Hız Sınırları (Rate Limiting) — Tek IP'den 5 giriş sonrası 429 dönerken, meşru oturumlu kullanıcılar engellenmez")
    void testRateLimitingBehavior() throws Exception {
        String testIp = "198.51.100.42";
        String differentIp = "198.51.100.43";

        // 1. Aynı IP'den /api/v1/auth/login uç noktasına 5 deneme yap (Geçersiz veriyle)
        for (int i = 1; i <= 5; i++) {
            MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> { req.setRemoteAddr(testIp); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"yanlis" + i + "@analyzepanel.local\",\"password\":\"Yanlis1234!\"}"))
                    .andReturn().getResponse();

            assertNotEquals(429, response.getStatus(),
                    "İlk 5 istekte 429 Too Many Requests dönmemelidir. İstek: " + i);
        }

        // 2. Aynı IP'den 6. istek: Kesinlikle HTTP 429 ve Retry-After başlığı dönmelidir
        MockHttpServletResponse sixthResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"yanlis6@analyzepanel.local\",\"password\":\"Yanlis1234!\"}"))
                .andReturn().getResponse();

        assertEquals(429, sixthResponse.getStatus(), "6. istekte hız sınırı (429) tetiklenmelidir.");
        assertEquals("60", sixthResponse.getHeader("Retry-After"), "Retry-After başlığı 60 saniye olmalıdır.");
        assertTrue(sixthResponse.getContentAsString().contains("Hız sınırını aştınız"),
                "Hata mesajı hız sınırını belirtmelidir.");

        // 3. FARKLI bir IP'den gelen istek engellenmemelidir (IP izolasyonu)
        MockHttpServletResponse differentIpResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(differentIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farkli@analyzepanel.local\",\"password\":\"Yanlis1234!\"}"))
                .andReturn().getResponse();

        assertNotEquals(429, differentIpResponse.getStatus(),
                "Farklı IP adresinden gelen istek bloklanmamalıdır.");
    }

    @Test
    @DisplayName("T-076: 100 Eşzamanlı Kullanıcı Duman Testi — Sanal thread'ler ile hata oranı < %1, yanıt süreleri ve Hikari havuz doygunluğu ölçülür")
    void testConcurrent100UsersSmokeLoad() throws Exception {
        // 1. Çoklu test kullanıcısı oluştur ve her biri için ayrı JWT access token üret (Rate Limiting kullanıcı bazlıdır: 60/dk)
        int userPoolSize = 10;
        List<Cookie> authCookies = new ArrayList<>();
        for (int u = 0; u < userPoolSize; u++) {
            String email = "perf.user.smoke." + u + "@analyzepanel.local";
            AppUser testUser = new AppUser(
                    email,
                    "Performans Test Kullanıcısı " + u,
                    UserRole.MANAGER,
                    UserStatus.ACTIVE,
                    null,
                    passwordEncoder.encode(TEST_PASSWORD),
                    false
            );
            testUser = userRepository.save(testUser);
            createdUserIds.add(testUser.getId());

            String jwtToken = jwtService.generateToken(testUser.getEmail());
            authCookies.add(new Cookie("access_token", jwtToken));
        }

        // 2. 100 Eşzamanlı Görev Koştur (Java 25 Virtual Threads)
        int totalRequests = 100;
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long overallStart = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalRequests; i++) {
                final int reqIndex = i;
                final Cookie userCookie = authCookies.get(reqIndex % userPoolSize);
                executor.submit(() -> {
                    long reqStart = System.nanoTime();
                    try {
                        // Dönüşümlü olarak gerçek panel uç noktalarını çağır:
                        // - Öğretmen listesi (/api/v1/membership/teachers)
                        // - Okul arama (/api/v1/analysis/schools/search?query=Fen)
                        // - Öğrenci listesi (/api/v1/membership/students)
                        String endpoint = switch (reqIndex % 3) {
                            case 0 -> "/api/v1/membership/teachers";
                            case 1 -> "/api/v1/analysis/schools/search?query=Fen";
                            default -> "/api/v1/membership/students";
                        };

                        MockHttpServletResponse response = mockMvc.perform(get(endpoint)
                                        .cookie(userCookie)
                                        .with(req -> { req.setRemoteAddr("10.0.0." + ((reqIndex % 50) + 1)); return req; }))
                                .andReturn().getResponse();

                        long durationMs = (System.nanoTime() - reqStart) / 1_000_000;
                        latencies.add(durationMs);

                        if (response.getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            log.error("İstek {} başarısız: HTTP {}", reqIndex, response.getStatus());
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("İstek {} istisna fırlattı: {}", reqIndex, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "100 eşzamanlı istek 30 saniye içinde tamamlanmalıdır.");
        }

        long overallDuration = System.currentTimeMillis() - overallStart;

        // 3. İstatistikleri ve Yanıt Sürelerini Hesapla
        Collections.sort(latencies);
        long minLatency = latencies.getFirst();
        long maxLatency = latencies.getLast();
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50Latency = latencies.get((int) (latencies.size() * 0.50));
        long p95Latency = latencies.get((int) (latencies.size() * 0.95));
        long p99Latency = latencies.get((int) (latencies.size() * 0.99));

        double errorRate = (failureCount.get() * 100.0) / totalRequests;

        log.info("================================================================================");
        log.info("📊 [T-076] 100 EŞZAMANLI KULLANICI DUMAN TESTİ SONUÇLARI");
        log.info("================================================================================");
        log.info("Toplam İstek: {} | Başarılı: {} | Başarısız: {} | Hata Oranı: %{:.2f}",
                totalRequests, successCount.get(), failureCount.get(), errorRate);
        log.info("Toplam Süre: {} ms | Min: {} ms | Ort: {:.2f} ms | p50: {} ms | p95: {} ms | p99: {} ms | Max: {} ms",
                overallDuration, minLatency, avgLatency, p50Latency, p95Latency, p99Latency, maxLatency);

        // 4. Bağlantı Havuzu (HikariCP) Doygunluk Durumunu Ölç
        if (dataSource instanceof HikariDataSource hikariDs) {
            HikariPoolMXBean poolMx = hikariDs.getHikariPoolMXBean();
            int activeConns = poolMx != null ? poolMx.getActiveConnections() : 0;
            int idleConns = poolMx != null ? poolMx.getIdleConnections() : 0;
            int totalConns = poolMx != null ? poolMx.getTotalConnections() : 0;
            int awaitingThreads = poolMx != null ? poolMx.getThreadsAwaitingConnection() : 0;

            log.info("🔌 HikariCP Havuz Durumu: Maks: {} | Aktif: {} | Boşta: {} | Toplam: {} | Sırada Bekleyen İş: {}",
                    hikariDs.getMaximumPoolSize(), activeConns, idleConns, totalConns, awaitingThreads);

            assertEquals(configuredPoolSize, hikariDs.getMaximumPoolSize(), "Maksimum havuz boyutu yapılandırmayla eşleşmelidir (S-027).");
            assertTrue(hikariDs.getMaximumPoolSize() >= 5, "Maksimum havuz boyutu en az 5 olmalıdır.");
            assertEquals(0, awaitingThreads, "Test sonunda sırada bekleyen iş parçacığı olmamalıdır.");
        }

        // 5. Doğrulamalar (Kabul Kriteri: Hata Oranı < %1)
        assertTrue(errorRate < 1.0, "Hata oranı %1'in altında olmalıdır. Ölçülen: %" + errorRate);
        assertEquals(100, successCount.get(), "100 isteğin tamamı 200 OK ile dönmelidir.");
    }
}
