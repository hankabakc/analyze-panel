package com.hankabakc.analyzepanel.auth;

import com.hankabakc.analyzepanel.auth.controller.AuthController;
import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.LoginAttemptService;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.core.security.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AuthBruteForceIntegrationTest: Giriş ucunun kaba kuvvet korumasını ve /auth/me davranışını sınar.
 *
 * <ul>
 *   <li>ENG-07 §3.1 (T-056B / B-1): IP başına dakikada 5 giriş isteği. IP yalnızca bağlantı adresinden okunur;
 *       istemcinin uydurduğu {@code X-Forwarded-For} başlığı sınırı atlatamaz.</li>
 *   <li>ENG-11 §1.3 (T-056B / B-2): Aynı e-postaya 5 ardışık hatalı denemeden sonra, IP değişse bile 429.</li>
 *   <li>ENG-11 §1.2: Var olan ve olmayan e-posta aynı eşikte aynı 429 yanıtını alır (kullanıcı sayımı yok).</li>
 * </ul>
 */
@SpringBootTest
class AuthBruteForceIntegrationTest {

    private static final String TEST_EMAIL = "entegrasyon.test@example.com";
    private static final String MISSING_EMAIL = "hic.olmayan.hesap@example.com";
    private static final String RAW_PASSWORD = "DogruSifre-2026!";
    private static final String WRONG_PASSWORD = "YanlisSifre-2026!";
    private static final String TEST_IP_PREFIX = "10.66.";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthController authController;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        loginAttemptService.resetAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        loginAttemptService.resetAll();
        // Bu sınıfın istekleri yalnızca 10.66.* adreslerinden gelir; başka hiçbir denetim satırına dokunulmaz
        auditLogRepository.findAll().stream()
                .filter(log -> log.getIpAddress() != null && log.getIpAddress().startsWith(TEST_IP_PREFIX))
                .forEach(auditLogRepository::delete);
        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
    }

    @Test
    @DisplayName("T-002 Entegrasyon: Oturum yokken /api/v1/auth/me 401 Unauthorized fırlatır")
    void testMe_Unauthenticated_Throws401() {
        SecurityContextHolder.clearContext();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authController.me());
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("T-002 Entegrasyon: Oturum açıkken /api/v1/auth/me geçerli UserDto döner")
    void testMe_Authenticated_ReturnsUserDto() {
        userRepository.save(new AppUser(TEST_EMAIL, "Ayşe Öğretmen", UserRole.TEACHER, UserStatus.ACTIVE));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_EMAIL, null, Collections.emptyList()));

        ApiResponse<UserDto> response = authController.me();

        assertNotNull(response);
        assertTrue(response.success());
        assertNotNull(response.data());
        assertEquals(TEST_EMAIL, response.data().email());
        assertEquals("Ayşe Öğretmen", response.data().fullName());
        assertEquals(UserRole.TEACHER, response.data().role());
    }

    @Test
    @DisplayName("ENG-07 §3.1: Aynı IP'den giriş ucuna 6. istek 429 ve Retry-After: 60 döner")
    void testIpRateLimit_SixthRequest_Returns429WithRetryAfter() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            assertEquals(200, filterLogin(filter, chain, "192.168.1.100", null).getStatus());
        }
        MockHttpServletResponse sixth = filterLogin(filter, chain, "192.168.1.100", null);

        assertEquals(429, sixth.getStatus());
        assertEquals("60", sixth.getHeader("Retry-After"));
        assertTrue(sixth.getContentAsString().contains("Hız sınırını aştınız"));
    }

    @Test
    @DisplayName("T-056B / B-1: Her istekte farklı X-Forwarded-For gönderilse de aynı bağlantıdan 6. istek 429 alır")
    void testIpRateLimit_SpoofedForwardedFor_CannotBypass() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 1; i <= 5; i++) {
            assertEquals(200, filterLogin(filter, chain, "192.168.1.200", "10.99.0." + i).getStatus());
        }
        assertEquals(429, filterLogin(filter, chain, "192.168.1.200", "10.99.0.6").getStatus());
    }

    @Test
    @DisplayName("T-056B / B-2: 5 hatalı denemeden sonra aynı e-posta IP değişse, büyük harfle yazılsa, şifre doğru olsa bile 429 + Retry-After alır")
    void testAccountBackoff_AfterFiveFailures_BlocksFromAnyIp() throws Exception {
        createUserWithPassword();

        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(TEST_EMAIL, WRONG_PASSWORD).getStatus());
        }
        MockHttpServletResponse sixth = login(TEST_EMAIL.toUpperCase(), WRONG_PASSWORD);
        MockHttpServletResponse correctPassword = login(TEST_EMAIL, RAW_PASSWORD);

        assertEquals(429, sixth.getStatus());
        assertEquals(429, correctPassword.getStatus(), "Bekleme süresince doğru şifre de içeri alınmamalı");
        long retryAfter = Long.parseLong(sixth.getHeader("Retry-After"));
        assertTrue(retryAfter > 0 && retryAfter <= 60, "İlk kademe 60 sn olmalı, gelen: " + retryAfter);
    }

    @Test
    @DisplayName("T-056B / ENG-11 §1.2: Var olan ve olmayan e-posta aynı eşikte, aynı mesaj ve aynı Retry-After ile 429 alır")
    void testAccountBackoff_ExistingAndMissingEmail_IdenticalResponse() throws Exception {
        createUserWithPassword();

        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(TEST_EMAIL, WRONG_PASSWORD).getStatus());
            assertEquals(401, login(MISSING_EMAIL, WRONG_PASSWORD).getStatus());
        }
        MockHttpServletResponse existing = login(TEST_EMAIL, WRONG_PASSWORD);
        MockHttpServletResponse missing = login(MISSING_EMAIL, WRONG_PASSWORD);

        assertEquals(429, existing.getStatus());
        assertEquals(429, missing.getStatus());
        long diff = Math.abs(Long.parseLong(existing.getHeader("Retry-After"))
                - Long.parseLong(missing.getHeader("Retry-After")));
        assertTrue(diff <= 1, "Retry-After farkı en fazla 1 sn olmalı, gelen fark: " + diff);
        assertEquals(existing.getContentAsString().replaceAll("\\d+", "#"),
                missing.getContentAsString().replaceAll("\\d+", "#"));
    }

    @Test
    @DisplayName("T-056B / B-2: Başarılı giriş sayacı sıfırlar; ardından gelen 5 hatalı deneme yine 401 alır")
    void testAccountBackoff_SuccessfulLogin_ResetsCounter() throws Exception {
        createUserWithPassword();

        for (int i = 0; i < 4; i++) {
            assertEquals(401, login(TEST_EMAIL, WRONG_PASSWORD).getStatus());
        }
        assertEquals(200, login(TEST_EMAIL, RAW_PASSWORD).getStatus());

        // Sayaç sıfırlanmasaydı 4 + 5 = 9 ardışık hata olurdu ve ikinci turun 2. denemesi 429 alırdı
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(TEST_EMAIL, WRONG_PASSWORD).getStatus());
        }
    }

    private void createUserWithPassword() {
        userRepository.save(new AppUser(TEST_EMAIL, "Kaba Kuvvet Test", UserRole.TEACHER, UserStatus.ACTIVE,
                null, passwordEncoder.encode(RAW_PASSWORD), false));
    }

    /** Her istek farklı bir bağlantı adresinden gelir; IP sınırına takılmadan yalnızca hesap bazlı koruma ölçülür. */
    private MockHttpServletResponse login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"rememberMe\":false}";
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(TEST_IP_PREFIX + IP_COUNTER.incrementAndGet());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse filterLogin(RateLimitingFilter filter, FilterChain chain,
                                                String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
