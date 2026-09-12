package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.entity.AnalysisReport;
import com.hankabakc.analyzepanel.analysis.repository.AnalysisReportRepository;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AnalysisSecurityIntegrationTest: T-024 / B-54 bulgusu kapsamında analiz modülündeki
 * yetki sınırlarını, IDOR/BOLA korumasını ve rol kapılarını MockMvc seviyesinde test eder.
 */
@SpringBootTest
public class AnalysisSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private com.hankabakc.analyzepanel.auth.service.RefreshTokenService refreshTokenService;

    @Autowired
    private com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository;

    private AppUser student1;
    private AppUser student2;
    private AppUser teacherPaired;
    private AppUser teacherUnpaired;
    private AppUser manager;
    private AnalysisReport report1;

    private final java.util.List<UUID> createdUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<UUID> createdReportIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final String S1_EMAIL = "s1.test.sec@analyzepanel.local";
    private static final String S2_EMAIL = "s2.test.sec@analyzepanel.local";
    private static final String T_PAIRED_EMAIL = "t.paired.sec@analyzepanel.local";
    private static final String T_UNPAIRED_EMAIL = "t.unpaired.sec@analyzepanel.local";
    private static final String MGR_EMAIL = "mgr.test.sec@analyzepanel.local";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanup();

        student1 = createUser(S1_EMAIL, "Öğrenci Bir", UserRole.STUDENT, 8);
        student2 = createUser(S2_EMAIL, "Öğrenci İki", UserRole.STUDENT, 8);
        teacherPaired = createUser(T_PAIRED_EMAIL, "Eşleşmiş Öğretmen", UserRole.TEACHER, null);
        teacherUnpaired = createUser(T_UNPAIRED_EMAIL, "Eşleşmemiş Öğretmen", UserRole.TEACHER, null);
        manager = createUser(MGR_EMAIL, "Yönetici Güvenlik", UserRole.MANAGER, null);

        // Öğrenci 1 ile Öğretmen Paired eşleştirilir
        membershipService.pairStudentWithTeacher(new PairingRequest(student1.getId(), teacherPaired.getId()));

        // Öğrenci 1 için örnek bir analiz raporu oluşturulur
        report1 = new AnalysisReport(UUID.randomUUID(), student1.getId(), "test-karne.pdf", "8. Sınıf Deneme Sınavı", 1);
        report1.setStatus("APPROVED");
        reportRepository.save(report1);
        createdReportIds.add(report1.getId());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private AppUser createUser(String email, String fullName, UserRole role, Integer grade) {
        AppUser user = new AppUser(email, fullName, role, UserStatus.ACTIVE, grade, "$2a$12$...", false);
        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private void cleanup() {
        for (UUID reportId : createdReportIds) {
            reportRepository.findById(reportId).ifPresent(reportRepository::delete);
        }
        createdReportIds.clear();

        if (auditLogRepository != null) {
            if (!createdUserIds.isEmpty()) {
                auditLogRepository.deleteByUserIdIn(createdUserIds);
            }
            auditLogRepository.deleteByUserEmailIn(java.util.List.of(
                T_PAIRED_EMAIL, T_UNPAIRED_EMAIL, MGR_EMAIL, S1_EMAIL, S2_EMAIL
            ));
        }

        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(u -> {
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        createdUserIds.clear();

        userRepository.findByEmail(S1_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(S2_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(T_PAIRED_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(T_UNPAIRED_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(MGR_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
    }

    // 1. Eşleşmemiş öğrenci başka öğrencinin karne detayına erişemez -> 403 Forbidden
    @Test
    @DisplayName("T-024 / B-54: Eşleşmesi olmayan öğrenci başka öğrencinin karne detayını isteyince 403 Forbidden döner")
    @WithMockUser(username = S2_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_OtherStudent_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId())
                        .requestAttr("userId", student2.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 2. Eşleşmemiş öğrenci başka öğrencinin kümülatif profiline erişemez -> 403 Forbidden
    @Test
    @DisplayName("T-024 / B-54: Eşleşmesi olmayan öğrenci başka öğrencinin kümülatif profilini isteyince 403 Forbidden döner")
    @WithMockUser(username = S2_EMAIL, roles = "STUDENT")
    void testGetCumulativeProfile_OtherStudent_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/profile/cumulative/" + student1.getId())
                        .requestAttr("userId", student2.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 3. Eşleşmemiş öğretmen başka öğrencinin karne detayına erişemez -> 403 Forbidden
    @Test
    @DisplayName("T-024 / B-54: Eşleşmemiş öğretmen başka öğrencinin karne detayını isteyince 403 Forbidden döner")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = "TEACHER")
    void testGetAnalysisDetail_UnpairedTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId())
                        .requestAttr("userId", teacherUnpaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 4. Eşleşmemiş öğretmen başka öğrencinin rapor listesine erişemez -> 403 Forbidden
    @Test
    @DisplayName("T-024 / B-54: Eşleşmemiş öğretmen başka öğrencinin rapor listesini isteyince 403 Forbidden döner")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = "TEACHER")
    void testGetReports_UnpairedTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/reports/" + student1.getId())
                        .requestAttr("userId", teacherUnpaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 5. Eşleşmemiş öğretmen başka öğrencinin kümülatif profiline erişemez -> 403 Forbidden
    @Test
    @DisplayName("T-024 / B-54: Eşleşmemiş öğretmen başka öğrencinin kümülatif profilini isteyince 403 Forbidden döner")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = "TEACHER")
    void testGetCumulativeProfile_UnpairedTeacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/profile/cumulative/" + student1.getId())
                        .requestAttr("userId", teacherUnpaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 6. Eşleşmemiş öğretmen başka öğrencinin raporunu silemez -> 403 Forbidden ve veritabanında durur
    @Test
    @DisplayName("T-024 / B-54: Eşleşmemiş öğretmen başka öğrencinin raporunu silmek istediğinde 403 Forbidden döner ve rapor silinmez")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = "TEACHER")
    void testDeleteReport_UnpairedTeacher_ReturnsForbidden_AndReportRemains() throws Exception {
        mockMvc.perform(delete("/api/v1/analysis/" + report1.getId())
                        .requestAttr("userId", teacherUnpaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertTrue(reportRepository.existsById(report1.getId()), "Yetkisiz silme denemesinde rapor veritabanında korunmalıdır.");
    }

    // 7. Eşleşmiş öğretmen kendi öğrencisinin karne detayını okuyabilir -> 200 OK
    @Test
    @DisplayName("T-024: Eşleşmiş öğretmen öğrencisinin karne detayına başarıyla erişir -> 200 OK")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testGetAnalysisDetail_PairedTeacher_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // 8. Öğrenci kendi karne detayını okuyabilir -> 200 OK
    @Test
    @DisplayName("T-024: Öğrenci kendi karne detayına başarıyla erişir -> 200 OK")
    @WithMockUser(username = S1_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_OwnStudent_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId())
                        .requestAttr("userId", student1.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // 9. Yönetici her rapora erişebilir -> 200 OK
    @Test
    @DisplayName("T-024: Yönetici her öğrencinin karne detayına başarıyla erişir -> 200 OK")
    @WithMockUser(username = MGR_EMAIL, roles = "MANAGER")
    void testGetAnalysisDetail_Manager_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId())
                        .requestAttr("userId", manager.getId().toString())
                        .requestAttr("role", "MANAGER")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // 10. Başarısız ayrıştırılmış rapora onay denemesi reddedilir -> 400 Bad Request (T-043B / B-81)
    @Test
    @DisplayName("T-043B: Başarısız ayrıştırılmış raporun onaylanması engellenir -> 400 Bad Request")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testApproveReport_FailedReport_ReturnsBadRequest() throws Exception {
        AnalysisReport failedReport = new AnalysisReport(UUID.randomUUID(), student1.getId(), "non-karne.pdf", "Analiz Başarısız", 1);
        failedReport.setStatus("FAILED");
        failedReport.setValidationErrors("Tanınmayan format");
        reportRepository.save(failedReport);
        createdReportIds.add(failedReport.getId());

        mockMvc.perform(post("/api/v1/analysis/approve/" + failedReport.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ayrıştırması başarısız olan bir analiz raporu onaylanamaz."));

        AnalysisReport reloaded = reportRepository.findById(failedReport.getId()).orElseThrow();
        assertEquals("FAILED", reloaded.getStatus(), "Başarısız raporun durumu değişmemelidir.");
    }

    // 11. Onay bekleyen geçerli rapor başarıyla onaylanır -> 200 OK (T-043B)
    @Test
    @DisplayName("T-043B: Onay bekleyen geçerli rapor başarıyla onaylanır -> 200 OK")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testApproveReport_PendingReport_ReturnsOk() throws Exception {
        AnalysisReport pendingReport = new AnalysisReport(UUID.randomUUID(), student1.getId(), "valid-karne.pdf", "8. Sınıf Deneme 2", 1);
        pendingReport.setStatus("PENDING_APPROVAL");
        reportRepository.save(pendingReport);
        createdReportIds.add(pendingReport.getId());

        mockMvc.perform(post("/api/v1/analysis/approve/" + pendingReport.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isOk());

        AnalysisReport reloaded = reportRepository.findById(pendingReport.getId()).orElseThrow();
        assertEquals("APPROVED", reloaded.getStatus(), "Onaylanan raporun durumu APPROVED olmalıdır.");
    }

    // 12. Zaten onaylanmış rapor tekrar onaylanamaz -> 400 Bad Request (T-043B)
    @Test
    @DisplayName("T-043B: Zaten onaylanmış raporun tekrar onaylanması engellenir -> 400 Bad Request")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testApproveReport_AlreadyApproved_ReturnsBadRequest() throws Exception {
        // report1 zaten setUp() içinde APPROVED olarak kaydedildi
        mockMvc.perform(post("/api/v1/analysis/approve/" + report1.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Yalnızca onay bekleyen raporlar onaylanabilir."));
    }

    // 13. Öğrenci henüz onaylanmamış bir rapora doğrudan URL ile erişemez -> 403 Forbidden (T-043B / B-81)
    @Test
    @DisplayName("T-043B: Öğrencinin onaylanmamış rapora doğrudan URL ile erişimi engellenir -> 403 Forbidden")
    @WithMockUser(username = S1_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_StudentUnapprovedReport_ReturnsForbidden() throws Exception {
        AnalysisReport unapprovedReport = new AnalysisReport(UUID.randomUUID(), student1.getId(), "karne.pdf", "Bekleyen Sınav", 1);
        unapprovedReport.setStatus("PENDING_APPROVAL");
        reportRepository.save(unapprovedReport);
        createdReportIds.add(unapprovedReport.getId());

        mockMvc.perform(get("/api/v1/analysis/details/" + unapprovedReport.getId())
                        .requestAttr("userId", student1.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 14. Ayrıştırması sürmekte olan (PROCESSING) rapora onay denemesi reddedilir -> 400 Bad Request (T-043C)
    @Test
    @DisplayName("T-043C: Ayrıştırması henüz süren raporun onaylanması engellenir -> 400 Bad Request")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testApproveReport_ProcessingReport_ReturnsBadRequest() throws Exception {
        AnalysisReport processingReport = new AnalysisReport(UUID.randomUUID(), student1.getId(), "processing.pdf", "Analiz Ediliyor...", 1);
        processingReport.setStatus("PROCESSING");
        reportRepository.save(processingReport);
        createdReportIds.add(processingReport.getId());

        mockMvc.perform(post("/api/v1/analysis/approve/" + processingReport.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ayrıştırması henüz tamamlanmamış bir rapor onaylanamaz."));

        AnalysisReport reloaded = reportRepository.findById(processingReport.getId()).orElseThrow();
        assertEquals("PROCESSING", reloaded.getStatus(), "Ayrıştırma sürecindeki raporun durumu değişmemelidir.");
    }

    // 15. Ayrıştırması sürmekte olan (PROCESSING) rapora red denemesi reddedilir -> 400 Bad Request (T-043C)
    @Test
    @DisplayName("T-043C: Ayrıştırması henüz süren raporun reddedilmesi engellenir -> 400 Bad Request")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = "TEACHER")
    void testRejectReport_ProcessingReport_ReturnsBadRequest() throws Exception {
        AnalysisReport processingReport = new AnalysisReport(UUID.randomUUID(), student1.getId(), "processing.pdf", "Analiz Ediliyor...", 1);
        processingReport.setStatus("PROCESSING");
        reportRepository.save(processingReport);
        createdReportIds.add(processingReport.getId());

        mockMvc.perform(post("/api/v1/analysis/reject/" + processingReport.getId())
                        .requestAttr("userId", teacherPaired.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ayrıştırması henüz tamamlanmamış bir rapor reddedilemez."));

        AnalysisReport reloaded = reportRepository.findById(processingReport.getId()).orElseThrow();
        assertEquals("PROCESSING", reloaded.getStatus(), "Ayrıştırma sürecindeki raporun durumu değişmemelidir.");
    }
}
