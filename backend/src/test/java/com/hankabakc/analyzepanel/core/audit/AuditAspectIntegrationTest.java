package com.hankabakc.analyzepanel.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.dto.LoginRequest;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuditAspectIntegrationTest: T-041D / B-78 bulgusu kapsamında
 * AuditAspect bileşeninin başarılı giriş işlemlerinde denetim izine
 * ANONYMOUS yerine gerçek kullanıcının e-posta adresini yazdığını test eder.
 */
@SpringBootTest
public class AuditAspectIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String TEST_EMAIL = "audit.aspect.test@analyzepanel.local";
    private static final String TEST_PASSWORD = "ValidPassword123";

    private AppUser testUser;
    private LocalDateTime testStartTime;

    @BeforeEach
    void setUp() {
        testStartTime = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        cleanup();

        testUser = new AppUser(
                TEST_EMAIL,
                "Audit Aspect Test Kullanıcısı",
                UserRole.MANAGER,
                UserStatus.ACTIVE,
                null,
                passwordEncoder.encode(TEST_PASSWORD),
                false
        );
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (testStartTime != null) {
            if (testUser != null && testUser.getId() != null) {
                auditLogRepository.deleteByUserIdAndTimestampGreaterThanEqual(testUser.getId(), testStartTime);
            }
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(TEST_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual("ANONYMOUS", testStartTime);
        }
        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
    }

    @Test
    @DisplayName("T-042 / T-041D: Başarılı girişte AuditAspect denetim izine ANONYMOUS veya düz metin e-posta yerine kullanıcının UUID kimliğini kaydeder")
    void testLogin_Success_AuditLogRecordsUserIdInsteadOfPlaintextEmail() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // audit_logs tablosunda testStartTime'dan sonra yazılan LOGIN kaydını ara
        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> "LOGIN".equals(l.getAction()) && !l.getTimestamp().isBefore(testStartTime))
                .toList();

        assertFalse(logs.isEmpty(), "Başarılı giriş sonrasında en az bir LOGIN denetim kaydı oluşmalıdır");

        // T-042: Kayıttaki kimlik kullanıcının gerçek UUID id'si olmalıdır
        AuditLog loginLog = logs.get(logs.size() - 1);
        assertEquals(testUser.getId(), loginLog.getUserId(),
                "Başarılı login sonrasında denetim kaydına kullanıcının UUID id'si yazılmalıdır");
        assertNotEquals(TEST_EMAIL, loginLog.getUserEmail(),
                "Denetim günlüğünde kullanıcının gerçek e-postası ASLA düz metin olarak yer almamalıdır");
        assertNull(loginLog.getUserEmail(),
                "Kullanıcı kimliği user_id ile tutulduğunda user_email null olmalıdır");
    }
}
