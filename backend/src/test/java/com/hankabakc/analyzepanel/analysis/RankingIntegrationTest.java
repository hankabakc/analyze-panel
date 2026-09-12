package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.entity.AnalysisReport;
import com.hankabakc.analyzepanel.analysis.repository.AnalysisReportRepository;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RankingIntegrationTest: T-038, T-038A ve T-038B kapsamında sınıf bazlı sıralama panosunu sınar.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-038B: Sıralama kurum genelinden sınıf bazına taşınmıştır. Her grup içinde sıra numarası
 *       1'den başlar. Maskeleme etiketi de grup içi sıraya bağlanır.</li>
 *   <li>APP-02 §1 & T-038B: Öğrenci yalnızca kendi grubunu alır. Başka sınıfın satırı yanıtta
 *       kesinlikle yer almaz — maskeli dahi gönderilmez.</li>
 *   <li>T-038A: Öğretmen ve yönetici tüm grupları gerçek adlarla görür (maskeleme yapılmaz).</li>
 *   <li>Sınıfsız öğrenciler "Sınıfsız" grubu altında listelenir; ekran boş kalmaz.</li>
 *   <li>T-013D/E & Hijyen: Test sonrası açılan sınıflar (classes) ve denetim izleri (RANKING_VIEW)
 *       temizlenir; DB'deki classes sayısı 0'a döner.</li>
 * </ul>
 */
@SpringBootTest
public class RankingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String S1_EMAIL = "s1.rank.test@analyzepanel.local";
    private static final String S2_EMAIL = "s2.rank.test@analyzepanel.local";
    private static final String S3_EMAIL = "s3.rank.test@analyzepanel.local";
    private static final String S4_EMAIL = "s4.rank.test@analyzepanel.local";
    private static final String TEACHER_EMAIL = "teacher.rank.test@analyzepanel.local";
    private static final String MANAGER_EMAIL = "manager.rank.test@analyzepanel.local";

    private UUID class10AId;
    private UUID class12BId;

    private AppUser student1;
    private AppUser student2;
    private AppUser student3;
    private AppUser student4;
    private AppUser teacher;
    private AppUser manager;

    private final List<UUID> createdClassIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdReportIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();

    private LocalDateTime testStartTime;

    @BeforeEach
    void setUp() {
        testStartTime = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Sınıflar oluşturulur
        class10AId = createClass("10-A");
        class12BId = createClass("12-B");

        // 2. Öğrenciler oluşturulur
        // Ali: 10-A, 85.5 net (10-A birincisi)
        student1 = new AppUser(S1_EMAIL, "Ali Demir", UserRole.STUDENT, UserStatus.ACTIVE, 10, "$2a$12$...", false);
        student1.setClassId(class10AId);
        student1 = userRepository.save(student1);
        createdUserIds.add(student1.getId());

        // Burak: 10-A, 65.0 net (10-A ikincisi)
        student2 = new AppUser(S2_EMAIL, "Burak Kaya", UserRole.STUDENT, UserStatus.ACTIVE, 10, "$2a$12$...", false);
        student2.setClassId(class10AId);
        student2 = userRepository.save(student2);
        createdUserIds.add(student2.getId());

        // Ayşe: 12-B, 90.0 net (12-B birincisi)
        student3 = new AppUser(S3_EMAIL, "Ayşe Çelik", UserRole.STUDENT, UserStatus.ACTIVE, 12, "$2a$12$...", false);
        student3.setClassId(class12BId);
        student3 = userRepository.save(student3);
        createdUserIds.add(student3.getId());

        // Cem: Sınıfsız, 75.0 net (Sınıfsız birincisi)
        student4 = new AppUser(S4_EMAIL, "Cem Can", UserRole.STUDENT, UserStatus.ACTIVE, null, "$2a$12$...", false);
        student4.setClassId(null);
        student4 = userRepository.save(student4);
        createdUserIds.add(student4.getId());

        // 3. Öğretmen ve Yönetici
        teacher = new AppUser(TEACHER_EMAIL, "Mehmet Hoca", UserRole.TEACHER, UserStatus.ACTIVE, null, "$2a$12$...", false);
        teacher = userRepository.save(teacher);
        createdUserIds.add(teacher.getId());

        manager = new AppUser(MANAGER_EMAIL, "Zeynep Müdür", UserRole.MANAGER, UserStatus.ACTIVE, null, "$2a$12$...", false);
        manager = userRepository.save(manager);
        createdUserIds.add(manager.getId());

        // 4. Onaylı deneme raporları eklenir
        createReportWithExam(student1.getId(), "Ali Deneme 1", 85.50);
        createReportWithExam(student2.getId(), "Burak Deneme 1", 65.00);
        createReportWithExam(student3.getId(), "Ayşe Deneme 1", 90.00);
        createReportWithExam(student4.getId(), "Cem Deneme 1", 75.00);
    }

    private UUID createClass(String name) {
        UUID id = UUID.randomUUID();
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO classes (id, name, created_at) VALUES (:id, :name, :now)")
                    .setParameter("id", id)
                    .setParameter("name", name)
                    .setParameter("now", OffsetDateTime.now())
                    .executeUpdate();
            return null;
        });
        createdClassIds.add(id);
        return id;
    }

    private void createReportWithExam(UUID studentId, String examName, double totalScore) {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), studentId, "karne.pdf", examName, 1);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        UUID reportId = report.getId();
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO exam_summaries (id, report_id, exam_name, exam_date, total_score) VALUES (:id, :rid, :name, :date, :score)")
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("rid", reportId)
                    .setParameter("name", examName)
                    .setParameter("date", LocalDate.now())
                    .setParameter("score", totalScore)
                    .executeUpdate();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (testStartTime != null) {
            for (UUID uid : createdUserIds) {
                auditLogRepository.deleteByUserIdAndTimestampGreaterThanEqual(uid, testStartTime);
            }
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(S1_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(S2_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(S3_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(S4_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(TEACHER_EMAIL, testStartTime);
            auditLogRepository.deleteByUserEmailAndTimestampGreaterThanEqual(MANAGER_EMAIL, testStartTime);
        }

        // 1. exam_summaries ve raporlar silinir
        for (UUID reportId : createdReportIds) {
            transactionTemplate.execute(status -> {
                entityManager.createNativeQuery("DELETE FROM exam_summaries WHERE report_id = :rid")
                        .setParameter("rid", reportId)
                        .executeUpdate();
                return null;
            });
            reportRepository.findById(reportId).ifPresent(reportRepository::delete);
        }
        createdReportIds.clear();

        // 2. Kullanıcılar silinir (FK çözülür)
        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(u -> {
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        createdUserIds.clear();

        userRepository.findByEmail(S1_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(S2_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(S3_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(S4_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(TEACHER_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });
        userRepository.findByEmail(MANAGER_EMAIL).ifPresent(u -> { refreshTokenService.deleteByUserId(u); userRepository.delete(u); });

        // 3. Sınıflar silinir
        transactionTemplate.execute(status -> {
            if (!createdClassIds.isEmpty()) {
                entityManager.createNativeQuery("DELETE FROM classes WHERE id IN (:ids)")
                        .setParameter("ids", createdClassIds)
                        .executeUpdate();
                createdClassIds.clear();
            }
            // T-038E: Önceki kesintilerden kalan sahipsiz test sınıflarını (öğrencisiz) temizle
            entityManager.createNativeQuery(
                    "DELETE FROM classes WHERE name IN ('10-A', '12-B') AND id NOT IN (SELECT DISTINCT class_id FROM app_users WHERE class_id IS NOT NULL)")
                    .executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("T-038B: Öğrenci yalnızca kendi sınıf grubunu alır; başka sınıfın satırı dahi gönderilmez")
    @WithMockUser(username = S1_EMAIL, roles = "STUDENT")
    void testRanking_AsStudent_SeesOnlyOwnClass_OthersOmitted_NoPii() throws Exception {
        var result = mockMvc.perform(get("/api/v1/analysis/ranking").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // Sadece 1 grup döner: 10-A
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].className").value("10-A"))
                .andExpect(jsonPath("$.data[0].classId").value(class10AId.toString()))
                .andExpect(jsonPath("$.data[0].entries", hasSize(2)))
                // Ali Demir: 1. sırada, isSelf = true, gerçek ad
                .andExpect(jsonPath("$.data[0].entries[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].entries[0].displayName").value("Ali Demir"))
                .andExpect(jsonPath("$.data[0].entries[0].isSelf").value(true))
                // Burak Kaya: 2. sırada, isSelf = false, grup içi sırayla maskeli: "2. Öğrenci"
                .andExpect(jsonPath("$.data[0].entries[1].rank").value(2))
                .andExpect(jsonPath("$.data[0].entries[1].displayName").value("2. Öğrenci"))
                .andExpect(jsonPath("$.data[0].entries[1].isSelf").value(false))
                .andReturn();

        String rawJson = result.getResponse().getContentAsString();
        // APP-02 §1 & T-038B: Başka sınıfın adı (12-B) veya başka sınıf öğrencisi (Ayşe, Cem) asla bulunmamalıdır
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("12-B"),
                "Öğrenci yanıtında başka bir sınıfın adı bulunmamalıdır");
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("Ayşe Çelik"),
                "Öğrenci yanıtında başka sınıftaki öğrencinin adı bulunmamalıdır");
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("Cem Can"),
                "Öğrenci yanıtında sınıfsız öğrencinin adı bulunmamalıdır");
        // Aynı sınıftaki diğer öğrencinin PII'si kesinlikle bulunmamalıdır
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("Burak Kaya"),
                "Öğrenci yanıtında sınıf arkadaşının gerçek adı kesinlikle bulunmamalıdır");
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains(student2.getId().toString()),
                "Öğrenci yanıtında sınıf arkadaşının ID'si kesinlikle bulunmamalıdır");
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("5551002233"),
                "Öğrenci yanıtında sınıf arkadaşının telefonu kesinlikle bulunmamalıdır");
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains(S2_EMAIL),
                "Öğrenci yanıtında sınıf arkadaşının e-postası kesinlikle bulunmamalıdır");
    }

    @Test
    @DisplayName("T-038B: Sınıfsız öğrenci 'Sınıfsız' grubunu alır; ekran boş kalmaz")
    @WithMockUser(username = S4_EMAIL, roles = "STUDENT")
    void testRanking_AsUnassignedStudent_SeesOnlyUnassignedGroup() throws Exception {
        var result = mockMvc.perform(get("/api/v1/analysis/ranking").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].className").value("Sınıfsız"))
                .andExpect(jsonPath("$.data[0].classId").doesNotExist())
                .andExpect(jsonPath("$.data[0].entries", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].entries[?(@.displayName == 'Cem Can' && @.isSelf == true)]").exists())
                .andReturn();

        String rawJson = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("10-A"));
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("12-B"));
        org.junit.jupiter.api.Assertions.assertFalse(rawJson.contains("Ali Demir"));
    }

    @Test
    @DisplayName("T-038A, T-038B & T-038E: Öğretmen tüm sınıfları görür, rank her grupta 1'den başlar, tüm adlar gerçektir")
    @WithMockUser(username = TEACHER_EMAIL, roles = "TEACHER")
    void testRanking_AsTeacher_SeesAllClasses_RankStartsFromOnePerGroup_AllRealNames() throws Exception {
        var mvcResult = mockMvc.perform(get("/api/v1/analysis/ranking").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // T-038E: En az 3 grup (10-A, 12-B, Sınıfsız) bulunmalıdır; başka sınıfların varlığı testi kırmaz
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].className", hasItems("10-A", "12-B", "Sınıfsız")))
                // Hiçbir grupta maskeleme etiketi yer almaz
                .andExpect(jsonPath("$.data[*].entries[*].displayName", not(hasItem(containsString(". Öğrenci")))))
                .andReturn();

        // T-038E: Sabit indeksler yerine testin oluşturduğu sınıfların DTO içeriği doğrulanır
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(mvcResult.getResponse().getContentAsString());
        com.fasterxml.jackson.databind.JsonNode data = root.get("data");

        com.fasterxml.jackson.databind.JsonNode group10A = null;
        com.fasterxml.jackson.databind.JsonNode group12B = null;
        com.fasterxml.jackson.databind.JsonNode groupUnassigned = null;

        for (com.fasterxml.jackson.databind.JsonNode group : data) {
            String cName = group.path("className").asText();
            if ("10-A".equals(cName)) group10A = group;
            else if ("12-B".equals(cName)) group12B = group;
            else if ("Sınıfsız".equals(cName)) groupUnassigned = group;
        }

        org.junit.jupiter.api.Assertions.assertNotNull(group10A, "10-A grubu yanıtta bulunmalıdır");
        org.junit.jupiter.api.Assertions.assertNotNull(group12B, "12-B grubu yanıtta bulunmalıdır");
        org.junit.jupiter.api.Assertions.assertNotNull(groupUnassigned, "Sınıfsız grubu yanıtta bulunmalıdır");

        // 10-A grubu: 2 test öğrencisi (Ali Demir rank 1, Burak Kaya rank 2)
        org.junit.jupiter.api.Assertions.assertEquals(class10AId.toString(), group10A.path("classId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, group10A.path("entries").size());
        org.junit.jupiter.api.Assertions.assertEquals(1, group10A.path("entries").get(0).path("rank").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Ali Demir", group10A.path("entries").get(0).path("displayName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, group10A.path("entries").get(1).path("rank").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Burak Kaya", group10A.path("entries").get(1).path("displayName").asText());

        // 12-B grubu: rank 1'den başlar! (Ayşe Çelik rank 1)
        org.junit.jupiter.api.Assertions.assertEquals(class12BId.toString(), group12B.path("classId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(1, group12B.path("entries").size());
        org.junit.jupiter.api.Assertions.assertEquals(1, group12B.path("entries").get(0).path("rank").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Ayşe Çelik", group12B.path("entries").get(0).path("displayName").asText());

        // Sınıfsız grubu: rank yine 1'den başlar ve Cem Can mevcuttur
        org.junit.jupiter.api.Assertions.assertTrue(groupUnassigned.path("entries").size() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(1, groupUnassigned.path("entries").get(0).path("rank").asInt(),
                "Sınıfsız grubunda da ilk öğrencinin sırası 1 olmalıdır");
        boolean cemFound = false;
        for (com.fasterxml.jackson.databind.JsonNode entry : groupUnassigned.path("entries")) {
            if ("Cem Can".equals(entry.path("displayName").asText())) {
                cemFound = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(cemFound, "Sınıfsız grubunda Cem Can bulunmalıdır");
    }

    @Test
    @DisplayName("T-038A, T-038B & T-038E: Yönetici tüm sınıfları ve gerçek adları görür")
    @WithMockUser(username = MANAGER_EMAIL, roles = "MANAGER")
    void testRanking_AsManager_SeesAllClasses_AllRealNames() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/ranking").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // T-038E: En az 3 grup bulunmalıdır; başka sınıfların varlığı testi kırmaz
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].className", hasItems("10-A", "12-B", "Sınıfsız")))
                .andExpect(jsonPath("$.data[*].entries[*].displayName", not(hasItem(containsString(". Öğrenci")))));
    }
}
