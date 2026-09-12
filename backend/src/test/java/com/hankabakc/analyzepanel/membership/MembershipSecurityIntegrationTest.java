package com.hankabakc.analyzepanel.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.CreateUserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MembershipSecurityIntegrationTest: T-004B / B-10 bulgusu kapsamında
 * POST /api/v1/membership/users uç noktası üzerindeki @PreAuthorize("hasRole('MANAGER')")
 * rol kapısını ve yetki sınırlarını HTTP MockMvc katmanından test eder.
 * 
 * <p>Tasarım İlkeleri:</p>
 * <ul>
 *   <li>K1: @PreAuthorize bir proxy filtresi olduğu için controller seviyesinde sınanır.</li>
 *   <li>K2: Testler with(csrf()) ile CSRF token sağlayarak 403 sonucunun CSRF'ten değil
 *       kesinlikle rol yetersizliğinden kaynaklandığını garanti eder.</li>
 * </ul>
 */
@SpringBootTest
public class MembershipSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private com.hankabakc.analyzepanel.auth.service.RefreshTokenService refreshTokenService;

    @Autowired
    private com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository;

    private java.time.LocalDateTime testStartTime;

    private final java.util.List<java.util.UUID> trackedUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();


    @BeforeEach
    void setUp() {
        testStartTime = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (testStartTime != null) {
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual("user", testStartTime);
        }

        for (java.util.UUID id : trackedUserIds) {
            userRepository.findById(id).ifPresent(u -> {
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        trackedUserIds.clear();
    }

    @Test
    @DisplayName("B-10 Rol Kapısı: TEACHER rolü ile POST /membership/users çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "TEACHER")
    void testCreateUser_AsTeacher_ReturnsForbidden() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Güvenlik Testi Öğretmen",
                UserRole.STUDENT,
                10
        );

        mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-10 Rol Kapısı: STUDENT rolü ile POST /membership/users çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "STUDENT")
    void testCreateUser_AsStudent_ReturnsForbidden() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Güvenlik Testi Öğrenci",
                UserRole.STUDENT,
                10
        );

        mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-10 / B-14 Rol Kapısı: Kimlik doğrulanmamış (anonim) istek 401 Unauthorized döner")
    void testCreateUser_Unauthenticated_ReturnsUnauthorized() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Anonim İstek",
                UserRole.STUDENT,
                10
        );

        mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Oturum bulunamadı veya süresi doldu."));
    }

    @Test
    @DisplayName("B-10 Mutlu Yol: MANAGER rolü ile POST /membership/users çağrısı 200 OK ile kullanıcı oluşturur")
    @WithMockUser(roles = "MANAGER")
    void testCreateUser_AsManager_Succeeds() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Güvenlik Testi Yönetici",
                UserRole.TEACHER,
                null
        );

        var result = mockMvc.perform(post("/api/v1/membership/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.generatedPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
        String userId = root.path("data").path("user").path("id").asText();
        if (userId != null && !userId.isEmpty()) {
            trackedUserIds.add(java.util.UUID.fromString(userId));
        }
    }

    @Test
    @DisplayName("T-013 Rol Kapısı: TEACHER rolü ile POST /membership/users/{id}/password/reset çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "TEACHER")
    void testResetPassword_AsTeacher_ReturnsForbidden() throws Exception {
        AppUser student = new AppUser(
                "guvenlik.testi.ogrenci1@analyzepanel.local",
                "Güvenlik Öğrenci",
                UserRole.STUDENT,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                11,
                "$2a$12$e8Fj0...",
                false
        );
        student = userRepository.save(student);
        trackedUserIds.add(student.getId());

        mockMvc.perform(post("/api/v1/membership/users/" + student.getId() + "/password/reset")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-013 Rol Kapısı: STUDENT rolü ile POST /membership/users/{id}/password/reset çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "STUDENT")
    void testResetPassword_AsStudent_ReturnsForbidden() throws Exception {
        AppUser teacher = new AppUser(
                "guvenlik.testi.ogretmen1@analyzepanel.local",
                "Güvenlik Öğretmen",
                UserRole.TEACHER,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                null,
                "$2a$12$e8Fj0...",
                false
        );
        teacher = userRepository.save(teacher);
        trackedUserIds.add(teacher.getId());

        mockMvc.perform(post("/api/v1/membership/users/" + teacher.getId() + "/password/reset")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-013 Yetki Sınırı: Hedef MANAGER olduğunda POST /membership/users/{id}/password/reset 400 Bad Request döner")
    @WithMockUser(roles = "MANAGER")
    void testResetPassword_TargetIsManager_ReturnsBadRequest() throws Exception {
        AppUser manager = new AppUser(
                "guvenlik.testi.manager2@analyzepanel.local",
                "İkinci Yönetici",
                UserRole.MANAGER,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                null,
                "$2a$12$e8Fj0...",
                false
        );
        manager = userRepository.save(manager);
        trackedUserIds.add(manager.getId());

        mockMvc.perform(post("/api/v1/membership/users/" + manager.getId() + "/password/reset")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Yönetici hesabı için şifre sıfırlanamaz."));
    }

    @Test
    @DisplayName("T-013 Mutlu Yol: MANAGER rolü ile öğrenci şifresi sıfırlanır, tek seferlik şifre döner ve UserDto içinde şifre bulunmaz")
    @WithMockUser(roles = "MANAGER")
    void testResetPassword_AsManager_Succeeds() throws Exception {
        AppUser student = new AppUser(
                "guvenlik.testi.ogrenci2@analyzepanel.local",
                "Güvenlik Öğrenci 2",
                UserRole.STUDENT,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                12,
                "$2a$12$e8Fj0...",
                false
        );
        student = userRepository.save(student);
        trackedUserIds.add(student.getId());

        mockMvc.perform(post("/api/v1/membership/users/" + student.getId() + "/password/reset")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.generatedPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    @DisplayName("T-036 Rol Kapısı: TEACHER rolü ile GET /membership/pairings çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "TEACHER")
    void testGetAllPairings_AsTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/pairings")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-036 Rol Kapısı: STUDENT rolü ile GET /membership/pairings çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "STUDENT")
    void testGetAllPairings_AsStudent_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/pairings")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-036 Mutlu Yol: MANAGER rolü ile GET /membership/pairings çağrısı 200 OK döner")
    @WithMockUser(roles = "MANAGER")
    void testGetAllPairings_AsManager_Succeeds() throws Exception {
        mockMvc.perform(get("/api/v1/membership/pairings")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("T-036 Rol Kapısı: TEACHER rolü ile POST /membership/unpair çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "TEACHER")
    void testUnpair_AsTeacher_ReturnsForbidden() throws Exception {
        com.hankabakc.analyzepanel.membership.dto.PairingRequest request =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        mockMvc.perform(post("/api/v1/membership/unpair")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-036 Rol Kapısı: STUDENT rolü ile POST /membership/unpair çağrısı 403 Forbidden ile engellenir")
    @WithMockUser(roles = "STUDENT")
    void testUnpair_AsStudent_ReturnsForbidden() throws Exception {
        com.hankabakc.analyzepanel.membership.dto.PairingRequest request =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        mockMvc.perform(post("/api/v1/membership/unpair")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-036 Mutlu Yol: MANAGER rolü ile POST /membership/unpair çağrısı eşleşmeyi başarıyla sonlandırır")
    @WithMockUser(roles = "MANAGER")
    void testUnpair_AsManager_Succeeds() throws Exception {
        AppUser teacher = new AppUser(
                "guvenlik.unpair.ogretmen@analyzepanel.local",
                "Unpair Güvenlik Öğretmen",
                UserRole.TEACHER,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                null,
                "$2a$12$e8Fj0...",
                false
        );
        teacher = userRepository.save(teacher);
        trackedUserIds.add(teacher.getId());

        AppUser student = new AppUser(
                "guvenlik.unpair.ogrenci@analyzepanel.local",
                "Unpair Güvenlik Öğrenci",
                UserRole.STUDENT,
                com.hankabakc.analyzepanel.auth.enums.UserStatus.ACTIVE,
                10,
                "$2a$12$e8Fj0...",
                false
        );
        student = userRepository.save(student);
        trackedUserIds.add(student.getId());

        // Eşleştir
        mockMvc.perform(post("/api/v1/membership/pair")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.hankabakc.analyzepanel.membership.dto.PairingRequest(student.getId(), teacher.getId()))))
                .andExpect(status().isOk());

        // Ayır
        mockMvc.perform(post("/api/v1/membership/unpair")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.hankabakc.analyzepanel.membership.dto.PairingRequest(student.getId(), teacher.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Eşleştirme başarıyla sonlandırıldı."));
    }

    // ---------------------------------------------------------------- T-040: Sınıf yönetimi rol kapısı

    @Test
    @DisplayName("T-040 Rol Kapısı: TEACHER rolü ile GET /membership/classes çağrısı 403 alır")
    @WithMockUser(roles = "TEACHER")
    void testGetClasses_AsTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/classes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-040 Rol Kapısı: STUDENT rolü ile GET /membership/classes çağrısı 403 alır")
    @WithMockUser(roles = "STUDENT")
    void testGetClasses_AsStudent_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/classes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-040 Rol Kapısı: TEACHER rolü sınıf oluşturamaz (403)")
    @WithMockUser(roles = "TEACHER")
    void testCreateClass_AsTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/membership/classes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yetkisiz Sınıf\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-040 Rol Kapısı: STUDENT rolü sınıftan öğrenci çıkaramaz (403)")
    @WithMockUser(roles = "STUDENT")
    void testRemoveStudentFromClass_AsStudent_ReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/membership/classes/" + java.util.UUID.randomUUID()
                        + "/students/" + java.util.UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-037 Rol Kapısı: TEACHER rolü aktiflik özetini göremez (403)")
    @WithMockUser(roles = "TEACHER")
    void testActivity_AsTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/membership/activity"))
                .andExpect(status().isForbidden());
    }
}
