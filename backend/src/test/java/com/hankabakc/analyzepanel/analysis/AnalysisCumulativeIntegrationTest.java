package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.entity.AnalysisReport;
import com.hankabakc.analyzepanel.analysis.entity.LessonAnalysis;
import com.hankabakc.analyzepanel.analysis.entity.TopicDetail;
import com.hankabakc.analyzepanel.analysis.repository.AnalysisReportRepository;
import com.hankabakc.analyzepanel.analysis.repository.LessonAnalysisRepository;
import com.hankabakc.analyzepanel.analysis.repository.TopicDetailRepository;
import com.hankabakc.analyzepanel.analysis.service.ExamDate;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AnalysisCumulativeIntegrationTest: T-025 bulguları (B-55, B-56) kapsamında
 * gelişim dosyası (kümülatif analiz) motorunu ve ders/konu bütünlüğünü test eder.
 */
@SpringBootTest
public class AnalysisCumulativeIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private LessonAnalysisRepository lessonRepository;

    @Autowired
    private TopicDetailRepository topicRepository;

    @Autowired
    private com.hankabakc.analyzepanel.auth.service.RefreshTokenService refreshTokenService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private AppUser student;
    private AppUser teacher;
    private final java.util.List<UUID> createdUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<UUID> createdReportIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final String STUDENT_EMAIL = "cumul.test.student@analyzepanel.local";
    private static final String TEACHER_EMAIL = "cumul.test.teacher@analyzepanel.local";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanup();

        student = new AppUser(STUDENT_EMAIL, "Kümülatif Test Öğrenci", UserRole.STUDENT, UserStatus.ACTIVE, 8, "$2a$12$...", false);
        student = userRepository.save(student);
        createdUserIds.add(student.getId());

        teacher = new AppUser(TEACHER_EMAIL, "Kümülatif Test Öğretmen", UserRole.TEACHER, UserStatus.ACTIVE, null, "$2a$12$...", false);
        teacher = userRepository.save(teacher);
        createdUserIds.add(teacher.getId());

        transactionTemplate.execute(status -> entityManager.createNativeQuery(
                "INSERT INTO student_teacher_pairings (student_id, teacher_id) VALUES (:sid, :tid) ON CONFLICT DO NOTHING")
                .setParameter("sid", student.getId())
                .setParameter("tid", teacher.getId())
                .executeUpdate());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        transactionTemplate.execute(status -> entityManager.createNativeQuery(
                "DELETE FROM student_teacher_pairings WHERE student_id IN (SELECT id FROM app_users WHERE email IN (:se, :te)) OR teacher_id IN (SELECT id FROM app_users WHERE email IN (:se, :te))")
                .setParameter("se", STUDENT_EMAIL)
                .setParameter("te", TEACHER_EMAIL)
                .executeUpdate());

        for (UUID reportId : createdReportIds) {
            for (LessonAnalysis lesson : lessonRepository.findAllByReportId(reportId)) {
                topicRepository.deleteAll(topicRepository.findAllByLessonAnalysisId(lesson.getId()));
            }
            lessonRepository.deleteAll(lessonRepository.findAllByReportId(reportId));
            reportRepository.findById(reportId).ifPresent(reportRepository::delete);
        }
        createdReportIds.clear();

        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(u -> {
                refreshTokenService.deleteByUserId(u);
                userRepository.delete(u);
            });
        }
        createdUserIds.clear();

        userRepository.findByEmail(STUDENT_EMAIL).ifPresent(u -> {
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
        userRepository.findByEmail(TEACHER_EMAIL).ifPresent(u -> {
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
    }

    @Test
    @DisplayName("B-55: Onaylı rapor yokken kümülatif görünüm tekil karne sayılarına geri düşmez, boş/bilgilendirici durum döner")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_Cumulative_WhenNoApprovedReports_ReturnsEmptyInformative() throws Exception {
        // Öğrencinin yalnızca PENDING_APPROVAL durumunda 1 raporu var
        AnalysisReport pendingReport = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. LGS Deneme", 1);
        pendingReport.setStatus("PENDING_APPROVAL");
        pendingReport = reportRepository.save(pendingReport);
        createdReportIds.add(pendingReport.getId());

        LessonAnalysis lesson = new LessonAnalysis(UUID.randomUUID(), pendingReport.getId(), "Matematik", 10, 2, 0, BigDecimal.valueOf(83.33));
        lesson = lessonRepository.save(lesson);

        TopicDetail topic = new TopicDetail(UUID.randomUUID(), lesson.getId(), "Üslü İfadeler", "WRONG", null, 6, 5, 1);
        topicRepository.save(topic);

        mockMvc.perform(get("/api/v1/analysis/details/" + pendingReport.getId() + "?cumulative=true")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consolidatedResult.lessons", hasSize(0)))
                .andExpect(jsonPath("$.data.examList", hasSize(0)))
                .andExpect(jsonPath("$.data.strategicPriority", containsString("Henüz onaylanmış")));
    }

    @Test
    @DisplayName("B-56: 2 onaylı raporda kümülatif ders toplamı ile altındaki konu toplamları tam tutarlı ve birleşik gelir")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_Cumulative_WithTwoApprovedReports_AggregatesBothLessonsAndTopics() throws Exception {
        // 1. Onaylı Rapor
        AnalysisReport report1 = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. LGS Deneme", 1);
        report1.setStatus("APPROVED");
        report1 = reportRepository.save(report1);
        createdReportIds.add(report1.getId());

        LessonAnalysis l1 = lessonRepository.save(new LessonAnalysis(UUID.randomUUID(), report1.getId(), "Matematik", 10, 2, 0, BigDecimal.valueOf(83.33)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), l1.getId(), "Üslü İfadeler", "WRONG", null, 6, 5, 1));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), l1.getId(), "Köklü İfadeler", "WRONG", null, 6, 5, 1));

        // 2. Onaylı Rapor
        AnalysisReport report2 = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme2.pdf", "2. LGS Deneme", 1);
        report2.setStatus("APPROVED");
        report2 = reportRepository.save(report2);
        createdReportIds.add(report2.getId());

        LessonAnalysis l2 = lessonRepository.save(new LessonAnalysis(UUID.randomUUID(), report2.getId(), "Matematik", 8, 4, 0, BigDecimal.valueOf(66.67)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), l2.getId(), "Üslü İfadeler", "WRONG", null, 6, 4, 2));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), l2.getId(), "Köklü İfadeler", "WRONG", null, 6, 4, 2));

        mockMvc.perform(get("/api/v1/analysis/details/" + report1.getId() + "?cumulative=true")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consolidatedResult.lessons", hasSize(1)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].lessonName", is("Matematik")))
                // Kümülatif Ders Toplamı: 10 + 8 = 18 Doğru, 2 + 4 = 6 Yanlış
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].correct", is(18)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].wrong", is(6)))
                // Kümülatif Konu Toplamı (B-56 Düzeltmesi): Konular da 6 değil 12 soru olmalı!
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics", hasSize(2)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[0].totalQuestions", is(12)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[0].correctCount", is(9)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[0].wrongCount", is(3)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[1].totalQuestions", is(12)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[1].correctCount", is(9)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[1].wrongCount", is(3)))
                // Öncelikli Konular da kümülatif toplamlar üzerinden 12 soruluk konuları görmeli
                .andExpect(jsonPath("$.data.priorityLists.byPoints", hasSize(2)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].totalQuestions", is(12)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].wrongCount", is(3)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].lostPoints", is(13.3)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].chronic", is(true)));
    }

    @Test
    @DisplayName("Regresyon: Tekil karne modunda (cumulative=false) yalnızca ilgili raporun tekil sayıları döner")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testGetAnalysisDetail_SingleReportMode_ReturnsOnlySingleReportData() throws Exception {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. LGS Deneme", 1);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        LessonAnalysis l = lessonRepository.save(new LessonAnalysis(UUID.randomUUID(), report.getId(), "Matematik", 10, 2, 0, BigDecimal.valueOf(83.33)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), l.getId(), "Üslü İfadeler", "WRONG", null, 6, 5, 1));

        mockMvc.perform(get("/api/v1/analysis/details/" + report.getId() + "?cumulative=false")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].correct", is(10)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].wrong", is(2)))
                .andExpect(jsonPath("$.data.consolidatedResult.lessons[0].topics[0].totalQuestions", is(6)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].lostPoints", is(4.4)))
                .andExpect(jsonPath("$.data.priorityLists.byPoints[0].chronic", is(false)));
    }

    /**
     * T-030E: exam_date metin sütunuyken "ORDER BY exam_date" metin sıralaması yapıyordu ve
     * 09.02 satırı 11.01'den önce geliyordu. Sütun artık `date`; sıra gerçekten kronolojik.
     *
     * <p>Tarihler bilerek karışık eklenir: metin sıralaması ile kronolojik sıralamanın
     * ayrıştığı gün/ay kombinasyonları seçilmiştir. Sütun metne geri dönerse bu test kırılır.</p>
     */
    @Test
    @DisplayName("T-030E: Sınav listesi metin sırasına göre değil gerçek tarihe göre kronolojik döner")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testExamList_IsOrderedChronologicallyNotLexicographically() throws Exception {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. LGS Deneme", 3);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        // Metin sıralaması: 09.02 -> 11.01 -> 24.01   |   Kronolojik: 11.01 -> 24.01 -> 09.02
        insertExam(report.getId(), "Şubat Denemesi", "09.02.2026", 85.33);
        insertExam(report.getId(), "Ocak Denemesi", "11.01.2026", 84.67);
        insertExam(report.getId(), "Ocak Sonu Denemesi", "24.01.2026", 74.34);

        mockMvc.perform(get("/api/v1/analysis/details/" + report.getId() + "?cumulative=false")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examList", hasSize(3)))
                .andExpect(jsonPath("$.data.examList[0].examDate", is("11.01.2026")))
                .andExpect(jsonPath("$.data.examList[1].examDate", is("24.01.2026")))
                .andExpect(jsonPath("$.data.examList[2].examDate", is("09.02.2026")));
    }

    /** Sınav satırını doğrudan ekler; exam_summaries için repository yok, native sorgu kullanılır. */
    private void insertExam(UUID reportId, String name, String date, double score) {
        transactionTemplate.execute(status -> entityManager.createNativeQuery(
                "INSERT INTO exam_summaries (id, report_id, exam_name, exam_date, total_score) "
                        + "VALUES (:id, :rid, :name, :date, :score)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("rid", reportId)
                .setParameter("name", name)
                .setParameter("date", ExamDate.parse(date))
                .setParameter("score", BigDecimal.valueOf(score))
                .executeUpdate());
    }

    /**
     * T-027 (B-59): Kümülatif toplama eskiden ders başına iki ayrı sorgu açıyordu; istek başına
     * sorgu sayısı ders sayısıyla birlikte büyüyordu. Artık ders toplamları ve konu toplamları
     * ikişer düz sorgudan geliyor.
     *
     * <p>Eşik yerine <b>büyüme</b> ölçülür: 2 derslik ve 4 derslik senaryoda çalıştırılan sorgu
     * sayısı <b>aynı</b> olmalıdır. Döngü geri gelirse bu sayı ders başına artar ve test kırılır.</p>
     */
    @Test
    @DisplayName("T-027: Kümülatif sorgu sayısı ders sayısıyla birlikte artmaz (N+1 yok)")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testCumulativeQueryCount_DoesNotGrowWithLessonCount() throws Exception {
        long ikiDers = sorguSayisiOlc(2);
        cleanup();
        setUp();
        long dortDers = sorguSayisiOlc(4);

        assertEquals(ikiDers, dortDers,
                "Sorgu sayısı ders sayısıyla büyümemeli. 2 ders: " + ikiDers + ", 4 ders: " + dortDers
                        + ". Artıyorsa ders başına sorgu açan döngü geri gelmiş demektir (N+1).");
    }

    /** Verilen sayıda derse sahip onaylı bir rapor kurar, kümülatif çağrıyı yapar ve çalışan sorgu sayısını döndürür. */
    private long sorguSayisiOlc(int dersSayisi) throws Exception {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme.pdf", "Deneme", 1);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        for (int i = 1; i <= dersSayisi; i++) {
            LessonAnalysis lesson = lessonRepository.save(new LessonAnalysis(
                    UUID.randomUUID(), report.getId(), "Ders " + i, 10, 2, 0, BigDecimal.valueOf(83.33)));
            topicRepository.save(new TopicDetail(UUID.randomUUID(), lesson.getId(), "Konu " + i, "WRONG", null, 6, 5, 1));
        }

        org.hibernate.stat.Statistics stats =
                entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        mockMvc.perform(get("/api/v1/analysis/details/" + report.getId() + "?cumulative=true")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consolidatedResult.lessons", hasSize(dersSayisi)));

        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("T-039: Kümülatif modda Stratejik Öncelik sabit metin değil, en zayıf dersten türetilir")
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void testCumulativeStrategicPriority_DerivedFromWeakestLesson() throws Exception {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme.pdf", "Deneme", 1);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        // Fen: 8 doğru / 10 soru (%80)
        LessonAnalysis fen = lessonRepository.save(new LessonAnalysis(
                UUID.randomUUID(), report.getId(), "Fen Bilimleri", 8, 2, 0, BigDecimal.valueOf(80.0)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), fen.getId(), "Mevsimler", "WRONG", null, 10, 8, 2));

        // Matematik: 5 doğru / 10 soru (%50) - En zayıf ders
        LessonAnalysis mat = lessonRepository.save(new LessonAnalysis(
                UUID.randomUUID(), report.getId(), "Matematik", 5, 5, 0, BigDecimal.valueOf(50.0)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), mat.getId(), "Üslü İfadeler", "WRONG", null, 10, 5, 5));

        mockMvc.perform(get("/api/v1/analysis/details/" + report.getId() + "?cumulative=true")
                        .requestAttr("userId", student.getId().toString())
                        .requestAttr("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategicPriority", is("Matematik dersine öncelik verilmeli (%50 başarı).")));
    }

    @Test
    @DisplayName("T-039: Kümülatif modda kronik konu varsa Öğretmen Aksiyon Planı kronik konuları vurgular")
    @WithMockUser(username = TEACHER_EMAIL, roles = "TEACHER")
    void testCumulativeTeacherActionPlan_WhenChronicTopicsExist_HighlightsChronicTopics() throws Exception {
        // 1. Rapor: Üslü İfadeler WRONG
        AnalysisReport report1 = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. Deneme", 1);
        report1.setStatus("APPROVED");
        report1 = reportRepository.save(report1);
        createdReportIds.add(report1.getId());

        LessonAnalysis mat1 = lessonRepository.save(new LessonAnalysis(
                UUID.randomUUID(), report1.getId(), "Matematik", 4, 6, 0, BigDecimal.valueOf(40.0)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), mat1.getId(), "Üslü İfadeler", "WRONG", null, 10, 4, 6));

        // 2. Rapor: Üslü İfadeler yine WRONG (Kronikleşti)
        AnalysisReport report2 = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme2.pdf", "2. Deneme", 1);
        report2.setStatus("APPROVED");
        report2 = reportRepository.save(report2);
        createdReportIds.add(report2.getId());

        LessonAnalysis mat2 = lessonRepository.save(new LessonAnalysis(
                UUID.randomUUID(), report2.getId(), "Matematik", 3, 7, 0, BigDecimal.valueOf(30.0)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), mat2.getId(), "Üslü İfadeler", "WRONG", null, 10, 3, 7));

        mockMvc.perform(get("/api/v1/analysis/details/" + report2.getId() + "?cumulative=true")
                        .requestAttr("userId", teacher.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherActionPlan", containsString("Kronik sorunlu konular: Üslü İfadeler.")))
                .andExpect(jsonPath("$.data.teacherActionPlan", containsString("temel kavram tekrarları ve hedefe yönelik soru çalışmaları")));
    }

    @Test
    @DisplayName("T-039: Tekil karne modunda Öğretmen Aksiyon Planı puan kaybettiren konulardan dinamik türetilir")
    @WithMockUser(username = TEACHER_EMAIL, roles = "TEACHER")
    void testSingleExamTeacherActionPlan_DerivedFromPointsLost() throws Exception {
        AnalysisReport report = new AnalysisReport(UUID.randomUUID(), student.getId(), "deneme1.pdf", "1. Deneme", 1);
        report.setStatus("APPROVED");
        report = reportRepository.save(report);
        createdReportIds.add(report.getId());

        LessonAnalysis mat = lessonRepository.save(new LessonAnalysis(
                UUID.randomUUID(), report.getId(), "Matematik", 4, 6, 0, BigDecimal.valueOf(40.0)));
        topicRepository.save(new TopicDetail(UUID.randomUUID(), mat.getId(), "Çarpanlar ve Katlar", "WRONG", null, 10, 4, 6));

        mockMvc.perform(get("/api/v1/analysis/details/" + report.getId() + "?cumulative=false")
                        .requestAttr("userId", teacher.getId().toString())
                        .requestAttr("role", "TEACHER")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherActionPlan", containsString("En çok puan kaybettiren konular: Çarpanlar ve Katlar.")))
                .andExpect(jsonPath("$.data.teacherActionPlan", containsString("soru çözümü ve pekiştirme çalışmaları önerilir.")));
    }
}
