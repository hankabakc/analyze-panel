package com.hankabakc.analyzepanel.auth;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.LoginAttemptService;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CookieAndCorsConfigIntegrationTest: Oturum çerezinin bayraklarının ve izin verilen kaynakların
 * yapılandırmadan geldiğini doğrular (T-069 / S-027).
 *
 * <p>Canlıda `app.cookie.secure=true` ve yalnızca kendi alan adı tanımlıdır; bu sınıf o ortamı
 * taklit eder. Değerler koda gömülü olsaydı bu testlerin hiçbiri kırılmazdı — asıl ölçtükleri,
 * ayarın gerçekten okunduğu.</p>
 */
@SpringBootTest(properties = {
        "app.cookie.secure=true",
        "app.cookie.same-site=Strict",
        "app.cors.allowed-origins=https://ornekkurum.example, https://www.ornekkurum.example"
})
class CookieAndCorsConfigIntegrationTest {

    private static final String TEST_EMAIL = "cerez.ayar.test@analyzepanel.local";
    private static final String RAW_PASSWORD = "CerezTesti-2026!";
    private static final String TEST_IP = "10.67.0.1";

    @Autowired
    private WebApplicationContext context;

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
        userRepository.save(new AppUser(TEST_EMAIL, "Çerez Ayar Testi", UserRole.TEACHER, UserStatus.ACTIVE,
                null, passwordEncoder.encode(RAW_PASSWORD), false));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        loginAttemptService.resetAll();
        // Yalnızca bu sınıfın kendi kullanıcısı ve kendi adresinden yazılan denetim satırları silinir.
        auditLogRepository.findAll().stream()
                .filter(log -> TEST_IP.equals(log.getIpAddress()))
                .forEach(auditLogRepository::delete);
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            refreshTokenService.deleteByUserId(user);
            userRepository.delete(user);
        });
    }

    @Test
    @DisplayName("T-069: Oturum çerezleri yapılandırmadaki Secure ve SameSite değerleriyle yazılır")
    void testLoginCookies_UseConfiguredFlags() throws Exception {
        MockHttpServletResponse response = login();

        assertEquals(200, response.getStatus());
        for (String name : new String[]{"access_token", "refresh_token"}) {
            Cookie cookie = response.getCookie(name);
            assertNotNull(cookie, name + " çerezi yazılmalı");
            assertTrue(cookie.getSecure(), name + " çerezi Secure olmalı (yalnızca HTTPS)");
            assertTrue(cookie.isHttpOnly(), name + " çerezi HttpOnly olmalı");
            assertEquals("Strict", cookie.getAttribute("SameSite"), name + " çerezinin SameSite değeri ayardan gelmeli");
        }
        assertTrue(response.getHeaders("Set-Cookie").stream().allMatch(header -> header.contains("Secure")),
                "Set-Cookie başlıkları Secure bayrağını taşımalı");
    }

    @Test
    @DisplayName("T-069: Yalnızca yapılandırmada yazan kaynak CORS ön kontrolünden geçer")
    void testCors_OnlyConfiguredOriginsAllowed() throws Exception {
        MockHttpServletResponse allowed = preflight("https://ornekkurum.example");
        assertEquals(200, allowed.getStatus());
        assertEquals("https://ornekkurum.example", allowed.getHeader("Access-Control-Allow-Origin"));

        // Boşlukla ayrılmış ikinci kaynak da kırpılıp tanınır.
        assertEquals("https://www.ornekkurum.example",
                preflight("https://www.ornekkurum.example").getHeader("Access-Control-Allow-Origin"));

        MockHttpServletResponse rejected = preflight("https://baska-site.example");
        assertEquals(403, rejected.getStatus(), "Tanımsız kaynak reddedilmeli");
        assertNull(rejected.getHeader("Access-Control-Allow-Origin"));
    }

    private MockHttpServletResponse login() throws Exception {
        String body = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\",\"rememberMe\":false}";
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(TEST_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn()
                .getResponse();
    }
}
