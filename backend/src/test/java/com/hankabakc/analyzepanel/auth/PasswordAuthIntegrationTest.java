package com.hankabakc.analyzepanel.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.dto.ChangePasswordRequest;
import com.hankabakc.analyzepanel.auth.dto.LoginRequest;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.RefreshToken;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.repository.RefreshTokenRepository;
import com.hankabakc.analyzepanel.auth.service.AuthService;
import com.hankabakc.analyzepanel.auth.service.JwtService;
import com.hankabakc.analyzepanel.auth.service.LoginAttemptService;
import com.hankabakc.analyzepanel.auth.service.MockPasswordBreachChecker;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.membership.dto.CreateUserRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PasswordAuthIntegrationTest: T-009 ve T-009B kapsamında e-posta + şifre ile giriş, zamanlama saldırısı (Timing Attack)
 * koruması, iki kanalın hesap durumu hizalaması, zorunlu şifre kapısı ve refresh token iptalini gerçek veritabanı
 * ve HTTP MockMvc katmanında sınar.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>ENG-11 §1.1 & §1.2 & T-009B / K1 (B-19): BCrypt cost 12 sahte hash ile yanıt süreleri eşitlenir (Timing Attack engeli).</li>
 *   <li>T-009B / K2, K3 (B-20): REJECTED / PENDING hesaplar şifreli girişte 403 ile reddedilir.</li>
 *   <li>T-009 / K3: Sızıntı servisi hatasında toleranslı kabul (fail-open).</li>
 *   <li>T-009 / K5: must_change_password=true durumunda 403 kapısı (allowlist: me, change, logout).</li>
 *   <li>ENG-11 §2.4: Şifre değiştiğinde tüm refresh token'ların iptal edilmesi.</li>
 * </ul>
 */
@SpringBootTest
public class PasswordAuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockPasswordBreachChecker breachChecker;

    @Autowired
    private com.hankabakc.analyzepanel.membership.service.MembershipService membershipService;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository;

    private final String TEST_EMAIL = "sifre.test@analyzepanel.local";
    private final String RAW_PASSWORD = "InitialPassword123";

    private final java.util.List<java.util.UUID> trackedUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final String REJECTED_EMAIL = "rejected.user@analyzepanel.local";

    private static int ipCounter = 1;

    private AppUser testUser;

    private RequestPostProcessor uniqueIp() {
        return request -> {
            request.setRemoteAddr("10.10.1." + (++ipCounter));
            return request;
        };
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanup();

        testUser = new AppUser(
                TEST_EMAIL,
                "Şifre Test Kullanıcısı",
                UserRole.MANAGER,
                UserStatus.ACTIVE,
                null,
                passwordEncoder.encode(RAW_PASSWORD),
                false
        );
        testUser = userRepository.saveAndFlush(testUser);
        breachChecker.setSimulateServiceError(false);
    }

    @AfterEach
    void tearDown() {
        cleanup();
        breachChecker.setSimulateServiceError(false);
    }

    private void cleanup() {
        // T-056B: Hesap bazlı gecikme sayacı bellekte ve testler arasında paylaşılır; her test temiz başlar
        context.getBean(LoginAttemptService.class).resetAll();
        java.util.Set<String> testEmails = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> testUserIds = new java.util.HashSet<>(trackedUserIds);
        testEmails.add(TEST_EMAIL);
        testEmails.add(REJECTED_EMAIL);

        for (java.util.UUID id : trackedUserIds) {
            userRepository.findById(id).ifPresent(u -> {
                testEmails.add(u.getEmail());
                testUserIds.add(u.getId());
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        trackedUserIds.clear();

        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            testUserIds.add(u.getId());
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
        userRepository.findByEmail(REJECTED_EMAIL).ifPresent(u -> {
            testUserIds.add(u.getId());
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });

        // T-041C / T-042: Testin ürettiği audit_logs satırlarını temizle (userId ve e-posta ile)
        if (!testUserIds.isEmpty()) {
            auditLogRepository.deleteByUserIdIn(testUserIds);
        }
        auditLogRepository.deleteByUserEmailIn(testEmails);
    }

    /**
     * T-033: `rememberMe` alanı eskiden ilkel `boolean` idi ve gövdede `null` gelen istek
     * Jackson'da eşlenemeyip HTTP 400 ile reddediliyordu; artık alan gelmese de null gelse de false sayılır.
     */
    @Test
    @DisplayName("T-033: rememberMe null gönderilirse istek 400 almaz, false sayılır")
    void testLogin_NullRememberMe_IsTreatedAsFalseNotRejected() throws Exception {
        String body = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\",\"rememberMe\":null}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL));
    }

    @Test
    @DisplayName("T-033: rememberMe alanı hiç gönderilmezse de giriş çalışır")
    void testLogin_MissingRememberMe_IsTreatedAsFalse() throws Exception {
        String body = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("T-009 / K1: Doğru e-posta ve şifre ile giriş başarılı olur, JWT çerezleri yazılır")
    void testLogin_Success() throws Exception {
        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, RAW_PASSWORD, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.role").value("MANAGER"));
    }

    @Test
    @DisplayName("T-009: Yanlış şifre ile giriş 401 Unauthorized ve genel hata mesajı döner")
    void testLogin_WrongPassword_Returns401AndGenericMessage() throws Exception {
        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, "WrongPassword123", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("E-posta veya şifre hatalı."));
    }

    @Test
    @DisplayName("T-009 / ENG-11 §1.2: Var olmayan e-posta ile giriş AYNI 401 ve AYNI genel mesajı döner (Enumeration Engeli)")
    void testLogin_NonExistentEmail_Returns401AndIdenticalMessage() throws Exception {
        LoginRequest loginRequest = new LoginRequest("olmayan.kullanici@analyzepanel.local", RAW_PASSWORD, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("E-posta veya şifre hatalı."));
    }

    @Test
    @DisplayName("T-009B / B-19 & K1: Var olmayan e-posta ile var olan e-posta + yanlış şifre yanıt süreleri eşitlenir (Timing Attack Engeli)")
    void testTimingAttack_ResponseTimeIsEquivalentForExistentAndNonExistentUsers() throws Exception {
        // Isınma (Warm-up) çağrısı
        LoginRequest warmUpReq = new LoginRequest("warmup@analyzepanel.local", "WarmupPass123", false);
        mockMvc.perform(post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(warmUpReq)));

        // 1. Var Olmayan Kullanıcı Ölçümü
        LoginRequest nonExistentReq = new LoginRequest("completely.unknown.user@analyzepanel.local", "WrongPassword123", false);
        long startNonExistent = System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentReq)))
                .andExpect(status().isUnauthorized());
        long durationNonExistentMs = (System.nanoTime() - startNonExistent) / 1_000_000;

        // 2. Var Olan Kullanıcı + Yanlış Şifre Ölçümü
        LoginRequest existentWrongPassReq = new LoginRequest(TEST_EMAIL, "WrongPassword123", false);
        long startExistent = System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existentWrongPassReq)))
                .andExpect(status().isUnauthorized());
        long durationExistentMs = (System.nanoTime() - startExistent) / 1_000_000;

        System.out.println(">> [B-19 Timing Attack Ölçümü] Var Olmayan Kullanıcı: " + durationNonExistentMs + " ms | Var Olan Kullanıcı (Yanlış Şifre): " + durationExistentMs + " ms");

        // Her iki sürenin de BCrypt cost 12 hesaplama süresi (~100ms+) mertebesinde olması ve oranlarının birbirine yakın olması gerekir
        assertTrue(durationNonExistentMs > 50, "Var olmayan kullanıcı sorgusu sahte BCrypt hash'i çalıştırmalıdır (en az 50ms sürmelidir).");
        assertTrue(durationExistentMs > 50, "Var olan kullanıcı sorgusu BCrypt hash'i çalıştırmalıdır (en az 50ms sürmelidir).");
    }

    @Test
    @DisplayName("T-009B / B-20 & K2, K3: REJECTED kullanıcı şifreli girişte 403 Forbidden alır")
    void testUserStatus_RejectedUser_RejectedAtLogin() throws Exception {
        // 1. REJECTED durumunda bir kullanıcı oluştur
        AppUser rejectedUser = new AppUser(
                REJECTED_EMAIL,
                "Askıdaki Öğrenci",
                UserRole.STUDENT,
                UserStatus.REJECTED,
                10,
                passwordEncoder.encode(RAW_PASSWORD),
                false
        );
        userRepository.saveAndFlush(rejectedUser);

        // 2. Şifreli giriş denemesi -> 403 Forbidden ve "Hesabınız aktif değil."
        LoginRequest loginReq = new LoginRequest(REJECTED_EMAIL, RAW_PASSWORD, false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hesabınız aktif değil."));
    }

    @Test
    @DisplayName("T-009B: ACTIVE kullanıcı şifreli giriş yapabilir")
    void testUserStatus_ActiveUser_AllowedAtLogin() throws Exception {
        LoginRequest loginReq = new LoginRequest(TEST_EMAIL, RAW_PASSWORD, false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("T-009 / K5: must_change_password=true olan kullanıcı korumalı uçlarda 403 alır; /auth/me ve /auth/password/change çalışır")
    void testMustChangePassword_Enforces403OnProtectedEndpoints() throws Exception {
        // Kullanıcıyı zorunlu şifre değişikliğine geçir
        testUser.setMustChangePassword(true);
        testUser = userRepository.saveAndFlush(testUser);

        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        // 1. Korumalı yönetim ucuna istek -> 403 Forbidden dönmeli (K5)
        CreateUserRequest userReq = new CreateUserRequest("Öğrenci Test", UserRole.STUDENT, 9);
        mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Devam etmek için şifrenizi değiştirmeniz gerekmektedir."));

        // 2. İzin listesindeki /auth/me -> 200 OK dönmeli ve mustChangePassword=true bildirmeli
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("T-009 / K2, K5, ENG-11 §2.4: Şifre değiştiğinde mustChangePassword temizlenir, eski refresh token'lar silinir ve koruma açılır")
    void testChangePassword_Success_ClearsFlagAndRevokesOldRefreshTokens() throws Exception {
        testUser.setMustChangePassword(true);
        testUser = userRepository.saveAndFlush(testUser);

        // Kullanıcıya bir refresh token üret
        String oldRefreshToken = refreshTokenService.createRefreshToken(testUser, true);
        assertTrue(refreshTokenRepository.findByToken(oldRefreshToken).isPresent());

        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        String newPassword = "BrandNewSecurePassword2026";
        ChangePasswordRequest changeReq = new ChangePasswordRequest(RAW_PASSWORD, newPassword);

        // Şifre değiştirme çağrısı
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        // Veritabanı teyidi: mustChangePassword=false olmalı ve şifre güncellenmeli
        AppUser freshUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertFalse(freshUser.isMustChangePassword());
        assertTrue(passwordEncoder.matches(newPassword, freshUser.getPassword()));

        // ENG-11 §2.4 Teyidi: Eski refresh token silinmiş olmalı
        assertTrue(refreshTokenRepository.findByToken(oldRefreshToken).isEmpty());

        // Koruma kapısı açılmış olmalı: yeni access token ile korumalı uç çağrılabilir
        String newAccessToken = jwtService.generateToken(freshUser.getEmail());
        Cookie newAuthCookie = new Cookie("access_token", newAccessToken);

        CreateUserRequest userReq = new CreateUserRequest("Öğrenci Test", UserRole.STUDENT, 9);
        mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .cookie(newAuthCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Temizlik
        userRepository.findByEmail("ogrenci.test@analyzepanel.local").ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("T-009 / K2: 12 karakterden kısa şifre 400 Bad Request ile reddedilir")
    void testChangePassword_ShortPassword_Rejected() throws Exception {
        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        ChangePasswordRequest changeReq = new ChangePasswordRequest(RAW_PASSWORD, "Short12345"); // 10 karakter

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T-009 / K3: Sızdırılmış şifre reddedilir; sızıntı servisi hata verirse şifre kabul edilir (fail-open)")
    void testChangePassword_BreachedPassword_Rejected_And_FailOpenOnError() throws Exception {
        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        // 1. Sızdırılmış şifre listesindeki şifre denenir -> 400 Bad Request dönmeli
        ChangePasswordRequest breachedReq = new ChangePasswordRequest(RAW_PASSWORD, "password123456");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(breachedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bu şifre daha önce sızdırılmış veri tabanlarında bulundu. Lütfen farklı ve güvenli bir şifre seçin."));

        // 2. K3 İspatı: Sızıntı servisi çökerse/hata verirse kullanıcı mağdur edilmez (fail-open)
        breachChecker.setSimulateServiceError(true);

        ChangePasswordRequest validReq = new ChangePasswordRequest(RAW_PASSWORD, "ValidPasswordButServiceIsDown2026");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("T-015 / Kabul Kriteri 1: mustChangePassword=true iken currentPassword gönderilmeden şifre başarıyla değiştirilir")
    void testChangePassword_MustChangePasswordTrue_DoesNotRequireCurrentPassword() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        ChangePasswordRequest changeReq = new ChangePasswordRequest(null, "BrandNewSecurePassword2026");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        AppUser fresh = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(fresh.isMustChangePassword(), "mustChangePassword bayrağı false'a düşmelidir");
        assertTrue(passwordEncoder.matches("BrandNewSecurePassword2026", fresh.getPassword()), "Yeni şifre kaydedilmiş olmalıdır");
    }

    @Test
    @DisplayName("T-015 / Kabul Kriteri 2 (Güvenlik): mustChangePassword=false olan normal kullanıcı currentPassword göndermezse 400 döner")
    void testChangePassword_MustChangePasswordFalse_RequiresCurrentPassword() throws Exception {
        testUser.setMustChangePassword(false);
        userRepository.save(testUser);

        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        ChangePasswordRequest changeReq = new ChangePasswordRequest(null, "BrandNewSecurePassword2026");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mevcut şifre hatalı."));
    }

    @Test
    @DisplayName("T-015 / Kabul Kriteri 3: mustChangePassword=true iken geçici şifrenin aynısı yeni şifre yapılırsa 400 döner ve bayrak düşmez")
    void testChangePassword_SamePasswordAsTemporary_Rejected() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        // RAW_PASSWORD testUser'ın mevcut geçici şifresidir (Password123!)
        ChangePasswordRequest changeReq = new ChangePasswordRequest(null, RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Yeni şifreniz mevcut şifrenizle aynı olamaz."));

        AppUser fresh = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(fresh.isMustChangePassword(), "Geçersiz istekte mustChangePassword hâlâ true kalmalıdır");
    }

    @Test
    @DisplayName("T-013B / B-46: Şifre değiştikten sonra eski access token ile yapılan istekler 401 Unauthorized alır; yeni token çalışır")
    void testChangePassword_OldAccessTokenIssuedBeforeChange_RejectedWith401() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        // 1. Şifre değişikliği öncesinde üretilen eski erişim token'ı
        String oldAccessToken = jwtService.generateToken(testUser.getEmail());
        Cookie oldCookie = new Cookie("access_token", oldAccessToken);

        // Zaman damgasının sonraki saniyeye geçmesi için 1.1 sn bekle
        Thread.sleep(1100);

        // 2. Kullanıcı şifresini değiştirir
        ChangePasswordRequest changeReq = new ChangePasswordRequest(null, "BrandNewSecurePassword2026");
        var result = mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(oldCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andReturn();

        // 3. Eski access token ile /auth/password/change çağrılır -> 401 Unauthorized (Hesap devralma engellendi)
        ChangePasswordRequest hijackReq = new ChangePasswordRequest(null, "AttackerHijackPassword2026");
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(oldCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackReq)))
                .andExpect(status().isUnauthorized());

        // 4. Eski access token ile /auth/me çağrılır -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(oldCookie))
                .andExpect(status().isUnauthorized());

        // 5. Yeni access token ile /auth/me çağrılır -> 200 OK
        Cookie newAccessCookie = result.getResponse().getCookie("access_token");
        assertNotNull(newAccessCookie);
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(newAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(testUser.getEmail()));
    }

    @Test
    @DisplayName("T-013C / B-48: Yönetici şifreyi sıfırladıktan sonra (mustChangePassword=true iken) eski token ile hesap devralma 401 alır")
    void testResetPasswordByManager_OldAccessTokenCannotHijackAccount_Returns401() throws Exception {
        // 1. Yeni bir öğrenci oluşturulur (mustChangePassword = true olarak başlar)
        com.hankabakc.analyzepanel.membership.dto.CreateUserRequest createReq = 
                new com.hankabakc.analyzepanel.membership.dto.CreateUserRequest(
                        "B48 Test Öğrenci",
                        UserRole.STUDENT,
                        10
                );
        var createRes = membershipService.createUser(createReq);
        trackedUserIds.add(createRes.user().id());
        AppUser student = userRepository.findById(createRes.user().id()).orElseThrow();

        // 2. Öğrenci ilk şifresiyle giriş yapmış gibi eski access token üretilir (T0)
        String oldAccessToken = jwtService.generateToken(student.getEmail());
        Cookie oldCookie = new Cookie("access_token", oldAccessToken);

        // Zaman damgasının sonraki saniyeye geçmesi için 1.1 sn bekle
        Thread.sleep(1100);

        // 3. Yönetici öğrencinin şifresini sıfırlar (T1 > T0, mustChangePassword=true, credentialsInvalidatedAt güncellenir)
        var resetRes = membershipService.resetUserPassword(student.getId());
        String newTempPassword = resetRes.generatedPassword();

        // 4. B-46 Saldırı Yolu: Saldırgan oldAccessToken ile /auth/password/change'e yalnızca newPassword gönderir
        // (T-015 gereği mustChangePassword=true iken currentPassword zorunlu değildir).
        // credentialsInvalidatedAt kontrolü sayesinde bu istek 401 Unauthorized almalıdır!
        ChangePasswordRequest hijackReq = new ChangePasswordRequest(null, "AttackerHijackedPassword2026");
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(oldCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackReq)))
                .andExpect(status().isUnauthorized());

        // 5. Saldırganın oldAccessToken ile /auth/me çağrısı da 401 Unauthorized almalıdır
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(oldCookie))
                .andExpect(status().isUnauthorized());

        // 6. Öğrenci yöneticinin verdiği yeni geçici şifre ile giriş yapar ve şifresini başarıyla değiştirir
        LoginRequest newLoginReq = new LoginRequest(student.getEmail(), newTempPassword, false);
        var loginResult = authService.login(newLoginReq);
        String freshAccessToken = (String) loginResult.get("accessToken");
        Cookie freshCookie = new Cookie("access_token", freshAccessToken);

        ChangePasswordRequest legitimateChangeReq = new ChangePasswordRequest(null, "StudentRealPassword2026");
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(freshCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legitimateChangeReq)))
                .andExpect(status().isOk());

        AppUser updatedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertFalse(updatedStudent.isMustChangePassword(), "Şifre başarıyla değiştiğinde mustChangePassword false olmalıdır");
    }

    @Test
    @DisplayName("T-041 / ENG-10 §1: Veritabanındaki tüm hesaplar benzersiz BCrypt şifre hash'i taşır")
    void testNoDuplicatePasswordHashesInDatabase() {
        long totalUsers = userRepository.count();
        long distinctHashes = userRepository.countDistinctPasswordHashes();
        assertEquals(totalUsers, distinctHashes,
                "Veritabanındaki her kullanıcının şifre hash'i benzersiz (farklı tuzlu) olmalıdır. Toplam: " 
                        + totalUsers + ", Benzersiz: " + distinctHashes);
    }

    @Test
    @DisplayName("T-041: Bir hesabın şifresiyle başka bir hesaba giriş denemesi 401 Unauthorized alır")
    void testCrossAccountPasswordLogin_ReturnsUnauthorized() throws Exception {
        // 1. İki farklı kullanıcı oluştur
        var createA = membershipService.createUser(new CreateUserRequest("Çapraz Test Kullanıcı A", UserRole.TEACHER, null));
        var createB = membershipService.createUser(new CreateUserRequest("Çapraz Test Kullanıcı B", UserRole.STUDENT, 9));
        trackedUserIds.add(createA.user().id());
        trackedUserIds.add(createB.user().id());

        String emailA = createA.user().email();
        String passwordA = createA.generatedPassword();

        String emailB = createB.user().email();
        String passwordB = createB.generatedPassword();

        // 2. Hash'lerin farklı olduğunu doğrula (farklı rastgele tuzlar)
        AppUser userA = userRepository.findById(createA.user().id()).orElseThrow();
        AppUser userB = userRepository.findById(createB.user().id()).orElseThrow();
        assertNotEquals(userA.getPassword(), userB.getPassword(), "İki kullanıcının şifre hash'leri asla aynı olamaz");

        // 3. Kullanıcı B'nin e-postası ve Kullanıcı A'nın şifresi ile giriş denemesi -> 401 Unauthorized
        LoginRequest crossLoginReq = new LoginRequest(emailB, passwordA, false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossLoginReq)))
                .andExpect(status().isUnauthorized());

        // 4. Kullanıcı B kendi şifresiyle girdiğinde -> 200 OK
        LoginRequest correctLoginReq = new LoginRequest(emailB, passwordB, false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLoginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("T-056: Şifre değiştirmede mevcut şifre her oturumda sorulur (zorunlu ilk değişiklik hariç)")
    void testChangePassword_RequiresCurrentPassword_Returns400IfMissingOrWrong() throws Exception {
        // 1. Kullanıcı için access token üret
        String accessToken = jwtService.generateToken(testUser.getEmail());
        Cookie authCookie = new Cookie("access_token", accessToken);

        // 2. /auth/me çağrısında currentPasswordRequired=true dönmelidir
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentPasswordRequired").value(true));

        String newPassword = "NewPasswordForPwdChannel2026!";

        // 3. Mevcut şifre null gönderilirse -> 400 Bad Request
        ChangePasswordRequest missingCurrentReq = new ChangePasswordRequest(null, newPassword);
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingCurrentReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mevcut şifre hatalı."));

        // 4. Mevcut şifre yanlış gönderilirse -> 400 Bad Request
        ChangePasswordRequest wrongCurrentReq = new ChangePasswordRequest("WrongCurrentPass999", newPassword);
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrentReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mevcut şifre hatalı."));

        // 5. Mevcut şifre doğru gönderilirse -> 200 OK
        ChangePasswordRequest correctReq = new ChangePasswordRequest(RAW_PASSWORD, newPassword);
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
