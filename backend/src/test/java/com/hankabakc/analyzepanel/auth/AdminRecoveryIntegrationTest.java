package com.hankabakc.analyzepanel.auth;

import com.hankabakc.analyzepanel.auth.cli.AdminRecoveryRunner;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminRecoveryIntegrationTest: CLI yönetici kurtarma komutu, ikinci yönetici oluşturma
 * ve yönetici sayımı uçlarını test eder (T-075 / S-027).
 */
@SpringBootTest
class AdminRecoveryIntegrationTest {

    @Autowired
    private AdminRecoveryRunner adminRecoveryRunner;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @Transactional
    @DisplayName("Yönetici kurtarma komutu başarıyla çalışır, şifreyi yeniler, must_change_password açar ve audit_logs kaydeder")
    void testAdminRecovery_Success() {
        // Mevcut bir test yöneticisi hazırla
        String email = "test.admin.recovery@analyzepanel.local";
        AppUser admin = new AppUser(
                email,
                "Kurtarma Yöneticisi",
                UserRole.MANAGER,
                UserStatus.ACTIVE,
                null,
                passwordEncoder.encode("OldPassword123!"),
                false
        );
        userRepository.save(admin);

        AtomicInteger exitCode = new AtomicInteger(-1);
        adminRecoveryRunner.setExitHandler(exitCode::set);

        // Komutu çalıştır: --recover-admin=test.admin.recovery@analyzepanel.local
        adminRecoveryRunner.run(new DefaultApplicationArguments("--recover-admin=" + email));

        assertEquals(0, exitCode.get(), "Kurtarma komutu çıkış kodu 0 olmalıdır.");

        AppUser updatedAdmin = userRepository.findByEmail(email).orElseThrow();
        assertTrue(updatedAdmin.isMustChangePassword(), "Kurtarma sonrası mustChangePassword true olmalıdır.");
        assertNotNull(updatedAdmin.getCredentialsInvalidatedAt(), "credentialsInvalidatedAt güncellenmiş olmalıdır.");
        assertFalse(passwordEncoder.matches("OldPassword123!", updatedAdmin.getPassword()), "Eski şifre artık geçersiz olmalıdır.");

        // Audit log doğrulaması
        List<AuditLog> logs = auditLogRepository.findAll();
        boolean hasAudit = logs.stream().anyMatch(l ->
                "ADMIN_RECOVERY".equals(l.getAction()) &&
                updatedAdmin.getId().equals(l.getUserId()) &&
                "127.0.0.1 (CLI)".equals(l.getIpAddress())
        );
        assertTrue(hasAudit, "audit_logs tablosunda ADMIN_RECOVERY işlemi bulunmalıdır.");
    }

    @Test
    @Transactional
    @DisplayName("Kayıtlı olmayan e-posta ile kurtarma denendiğinde hata döner")
    void testAdminRecovery_NotFound_ExitsWithError() {
        AtomicInteger exitCode = new AtomicInteger(-1);
        adminRecoveryRunner.setExitHandler(exitCode::set);

        adminRecoveryRunner.run(new DefaultApplicationArguments("--recover-admin=olmayan.yonetici@analyzepanel.local"));

        assertEquals(1, exitCode.get(), "Bulunamayan kullanıcı için çıkış kodu 1 olmalıdır.");
    }

    @Test
    @Transactional
    @DisplayName("Yönetici olmayan (öğretmen/öğrenci) kullanıcı için kurtarma denendiğinde hata döner")
    void testAdminRecovery_NotManager_ExitsWithError() {
        String email = "ogretmen.kurtarma@analyzepanel.local";
        AppUser teacher = new AppUser(
                email,
                "Öğretmen Kullanıcı",
                UserRole.TEACHER,
                UserStatus.ACTIVE,
                null,
                passwordEncoder.encode("TeacherPassword123!"),
                false
        );
        userRepository.save(teacher);

        AtomicInteger exitCode = new AtomicInteger(-1);
        adminRecoveryRunner.setExitHandler(exitCode::set);

        adminRecoveryRunner.run(new DefaultApplicationArguments("--recover-admin=" + email));

        assertEquals(1, exitCode.get(), "Yönetici olmayan kullanıcı için çıkış kodu 1 olmalıdır.");
    }

    @Test
    @Transactional
    @DisplayName("İkinci yönetici oluşturma komutu başarıyla çalışır ve denetim günlüğü kaydeder")
    void testCreateAdmin_Success() {
        String email = "ikinci.yonetici@analyzepanel.local";
        AtomicInteger exitCode = new AtomicInteger(-1);
        adminRecoveryRunner.setExitHandler(exitCode::set);

        adminRecoveryRunner.run(new DefaultApplicationArguments("--create-admin=" + email, "--name=İkinci Müdür"));

        assertEquals(0, exitCode.get(), "Yönetici oluşturma çıkış kodu 0 olmalıdır.");

        AppUser newAdmin = userRepository.findByEmail(email).orElseThrow();
        assertEquals("İkinci Müdür", newAdmin.getFullName());
        assertEquals(UserRole.MANAGER, newAdmin.getRole());
        assertTrue(newAdmin.isMustChangePassword());

        List<AuditLog> logs = auditLogRepository.findAll();
        boolean hasAudit = logs.stream().anyMatch(l ->
                "ADMIN_CREATE".equals(l.getAction()) &&
                newAdmin.getId().equals(l.getUserId())
        );
        assertTrue(hasAudit, "audit_logs tablosunda ADMIN_CREATE işlemi bulunmalıdır.");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/v1/membership/managers/count yöneticiler için aktif yönetici sayısını döner")
    void testGetManagersCount_AsManager_ReturnsCount() throws Exception {
        mockMvc.perform(get("/api/v1/membership/managers/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    @DisplayName("GET /api/v1/membership/managers/count öğretmenler için 403 Forbidden döner")
    void testGetManagersCount_AsTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/managers/count"))
                .andExpect(status().isForbidden());
    }
}
