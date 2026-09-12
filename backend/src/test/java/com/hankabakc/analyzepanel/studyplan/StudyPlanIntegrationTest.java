package com.hankabakc.analyzepanel.studyplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.analysis.entity.AnalysisReport;
import com.hankabakc.analyzepanel.analysis.entity.LessonAnalysis;
import com.hankabakc.analyzepanel.analysis.entity.TopicDetail;
import com.hankabakc.analyzepanel.analysis.repository.AnalysisReportRepository;
import com.hankabakc.analyzepanel.analysis.repository.LessonAnalysisRepository;
import com.hankabakc.analyzepanel.analysis.repository.TopicDetailRepository;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.studyplan.dto.CreateStudyPlanItemRequest;
import com.hankabakc.analyzepanel.studyplan.dto.CreateStudyPlanRequest;
import com.hankabakc.analyzepanel.studyplan.entity.StudyPlan;
import com.hankabakc.analyzepanel.studyplan.entity.StudyPlanItem;
import com.hankabakc.analyzepanel.studyplan.repository.StudyPlanItemRepository;
import com.hankabakc.analyzepanel.studyplan.repository.StudyPlanRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StudyPlanIntegrationTest: T-050A Çalışma Planı uç noktalarını, IDOR korumalarını,
 * konu doğrulamalarını ve veri tabanı durumunu MockMvc seviyesinde test eder.
 */
@SpringBootTest
public class StudyPlanIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private LessonAnalysisRepository lessonAnalysisRepository;

    @Autowired
    private TopicDetailRepository topicDetailRepository;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    @Autowired
    private StudyPlanItemRepository studyPlanItemRepository;

    private AppUser student1;
    private AppUser student2;
    private AppUser teacherPaired;
    private AppUser teacherUnpaired;

    private AnalysisReport report1;
    private LessonAnalysis lesson1;
    private TopicDetail topic1;
    private TopicDetail topic2;

    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdReportIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdLessonIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdTopicIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdPlanIds = new CopyOnWriteArrayList<>();

    private static final String S1_EMAIL = "t050a.s1@analyzepanel.local";
    private static final String S2_EMAIL = "t050a.s2@analyzepanel.local";
    private static final String T_PAIRED_EMAIL = "t050a.tpaired@analyzepanel.local";
    private static final String T_UNPAIRED_EMAIL = "t050a.tunpaired@analyzepanel.local";

    private static final String VALID_TOPIC_1 = "Çarpanlar ve Katlar";
    private static final String VALID_TOPIC_2 = "Kareköklü İfadeler";
    private static final String INVALID_TOPIC = "Uydurma Olmayan Konu";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Kullanıcılar oluşturulur
        student1 = createUser(S1_EMAIL, "Öğrenci Bir T050A", UserRole.STUDENT, 8);
        student2 = createUser(S2_EMAIL, "Öğrenci İki T050A", UserRole.STUDENT, 8);
        teacherPaired = createUser(T_PAIRED_EMAIL, "Eşleşmiş Öğretmen T050A", UserRole.TEACHER, null);
        teacherUnpaired = createUser(T_UNPAIRED_EMAIL, "Eşleşmemiş Öğretmen T050A", UserRole.TEACHER, null);

        // 2. Öğrenci 1 ile Öğretmen Paired eşleştirilir
        membershipService.pairStudentWithTeacher(new PairingRequest(student1.getId(), teacherPaired.getId()));

        // 3. Öğrenci 1 için örnek bir analiz raporu ve ayrıştırılmış konular hazırlanır
        report1 = new AnalysisReport(UUID.randomUUID(), student1.getId(), "t050a-karne.pdf", "8. Sınıf Deneme", 1);
        report1.setStatus("APPROVED");
        reportRepository.save(report1);
        createdReportIds.add(report1.getId());

        lesson1 = new LessonAnalysis(UUID.randomUUID(), report1.getId(), "Matematik", 15, 3, 2, new BigDecimal("75.00"));
        lessonAnalysisRepository.save(lesson1);
        createdLessonIds.add(lesson1.getId());

        topic1 = new TopicDetail(UUID.randomUUID(), lesson1.getId(), VALID_TOPIC_1, "WRONG", null, 4, 1, 3);
        topicDetailRepository.save(topic1);
        createdTopicIds.add(topic1.getId());

        topic2 = new TopicDetail(UUID.randomUUID(), lesson1.getId(), VALID_TOPIC_2, "CORRECT", null, 3, 3, 0);
        topicDetailRepository.save(topic2);
        createdTopicIds.add(topic2.getId());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        // Plan kalemleri ve planları temizle
        for (UUID planId : createdPlanIds) {
            try {
                studyPlanItemRepository.deleteAll(studyPlanItemRepository.findAllByPlanId(planId));
                studyPlanRepository.deleteById(planId);
            } catch (Exception ignored) {}
        }
        createdPlanIds.clear();

        // Konular ve dersleri temizle
        for (UUID topicId : createdTopicIds) {
            try { topicDetailRepository.deleteById(topicId); } catch (Exception ignored) {}
        }
        createdTopicIds.clear();

        for (UUID lessonId : createdLessonIds) {
            try { lessonAnalysisRepository.deleteById(lessonId); } catch (Exception ignored) {}
        }
        createdLessonIds.clear();

        for (UUID reportId : createdReportIds) {
            try { reportRepository.deleteById(reportId); } catch (Exception ignored) {}
        }
        createdReportIds.clear();

        if (student1 != null && teacherPaired != null) {
            try {
                membershipService.unpairStudentWithTeacher(new PairingRequest(student1.getId(), teacherPaired.getId()));
            } catch (Exception ignored) {}
        }
        if (student2 != null && teacherUnpaired != null) {
            try {
                membershipService.unpairStudentWithTeacher(new PairingRequest(student2.getId(), teacherUnpaired.getId()));
            } catch (Exception ignored) {}
        }

        // Kullanıcıları temizle
        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {}
        }
        createdUserIds.clear();
    }

    private AppUser createUser(String email, String fullName, UserRole role, Integer grade) {
        AppUser user = new AppUser(
                email,
                fullName,
                role,
                UserStatus.ACTIVE,
                grade,
                "$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFvX4e26tA7l9aI0M1E5jJ5m4m5a5m5a",
                false
        );
        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    // -------------------------------------------------------------
    // TEST 1: Eşleşmiş öğretmen plan oluşturur -> 200 ve DB'ye yansır
    // -------------------------------------------------------------
    @Test
    @DisplayName("Kriter 1: Eşleşmiş öğretmen geçerli konularla plan oluşturur -> 200")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = {"TEACHER"})
    void testCreatePlan_AsPairedTeacher_Success() throws Exception {
        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                student1.getId(),
                LocalDate.now().plusDays(7),
                List.of(
                        new CreateStudyPlanItemRequest(VALID_TOPIC_1, 40),
                        new CreateStudyPlanItemRequest(VALID_TOPIC_2, 25)
                )
        );

        String responseBody = mockMvc.perform(post("/api/v1/study-plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentId").value(student1.getId().toString()))
                .andExpect(jsonPath("$.data.teacherId").value(teacherPaired.getId().toString()))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].topicName").value(VALID_TOPIC_1))
                .andExpect(jsonPath("$.data.items[0].questionCount").value(40))
                .andExpect(jsonPath("$.data.items[0].completedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String planIdStr = objectMapper.readTree(responseBody).path("data").path("id").asText();
        UUID planId = UUID.fromString(planIdStr);
        createdPlanIds.add(planId);

        // Veritabanında doğrulama
        assertTrue(studyPlanRepository.existsById(planId));
        List<StudyPlanItem> items = studyPlanItemRepository.findAllByPlanId(planId);
        assertEquals(2, items.size());
    }

    // -------------------------------------------------------------
    // TEST 2: Eşleşmemiş öğretmen plan oluşturmayı dener -> 403
    // -------------------------------------------------------------
    @Test
    @DisplayName("Kriter 2: Eşleşmemiş öğretmen plan oluşturmayı dener -> 403 Forbidden")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = {"TEACHER"})
    void testCreatePlan_AsUnpairedTeacher_Forbidden() throws Exception {
        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                student1.getId(),
                LocalDate.now().plusDays(7),
                List.of(new CreateStudyPlanItemRequest(VALID_TOPIC_1, 30))
        );

        mockMvc.perform(post("/api/v1/study-plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("yetkiniz yok")));
    }

    // -------------------------------------------------------------
    // TEST 3: Başka öğrencinin planını okuma denemesi -> 403
    // -------------------------------------------------------------
    @Test
    @DisplayName("Kriter 3: Başka öğrencinin planını okuma denemesi -> 403 Forbidden")
    @WithMockUser(username = S2_EMAIL, roles = {"STUDENT"})
    void testGetPlansForStudent_AsOtherStudent_Forbidden() throws Exception {
        // Öğrenci 2, Öğrenci 1'in planlarını okumaya çalışır
        mockMvc.perform(get("/api/v1/study-plans/student/" + student1.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Sadece kendi")));
    }

    @Test
    @DisplayName("Kriter 3B: Eşleşmemiş öğretmenin öğrenci planını okuma denemesi -> 403 Forbidden")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = {"TEACHER"})
    void testGetPlansForStudent_AsUnpairedTeacher_Forbidden() throws Exception {
        // Eşleşmemiş öğretmen, Öğrenci 1'in planlarını okumaya çalışır
        mockMvc.perform(get("/api/v1/study-plans/student/" + student1.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("yetkiniz yok")));
    }

    // -------------------------------------------------------------
    // TEST 4: Başka öğrencinin kalemini işaretleme denemesi -> 403
    // -------------------------------------------------------------
    @Test
    @DisplayName("Kriter 4: Başka öğrencinin planındaki bir kalemi tamamlama denemesi -> 403 Forbidden")
    @WithMockUser(username = S2_EMAIL, roles = {"STUDENT"})
    void testCompleteItem_AsOtherStudent_Forbidden() throws Exception {
        // Öğrenci 1'e ait bir plan ve kalem oluşturulur
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        StudyPlanItem item = new StudyPlanItem(UUID.randomUUID(), plan.getId(), VALID_TOPIC_1, 20, null);
        studyPlanItemRepository.save(item);

        // Öğrenci 2 olarak Öğrenci 1'in görevini tamamlamaya çalışır
        mockMvc.perform(post("/api/v1/study-plans/items/" + item.getId() + "/complete")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Sadece kendi")));
    }

    // -------------------------------------------------------------
    // TEST 5: Ayrıştırılmamış konu adıyla plan oluşturma denemesi -> 400
    // -------------------------------------------------------------
    @Test
    @DisplayName("Kriter 5: Ayrıştırılmamış konu adıyla plan oluşturma denemesi -> 400 Bad Request")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = {"TEACHER"})
    void testCreatePlan_WithUnparsedTopic_BadRequest() throws Exception {
        // Uydurma/ayrıştırılmamış konu ile plan gönderilir
        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                student1.getId(),
                LocalDate.now().plusDays(7),
                List.of(new CreateStudyPlanItemRequest(INVALID_TOPIC, 50))
        );

        mockMvc.perform(post("/api/v1/study-plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ayrıştırılmış konuları arasında bulunmalıdır")));
    }

    // -------------------------------------------------------------
    // TEST 6: Öğrenci kendi planını başarıyla okur -> 200
    // -------------------------------------------------------------
    @Test
    @DisplayName("Ek Doğrulama: Öğrenci kendi çalışma planını başarıyla görüntüler -> 200")
    @WithMockUser(username = S1_EMAIL, roles = {"STUDENT"})
    void testGetPlansForStudent_AsSelf_Success() throws Exception {
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(3), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        StudyPlanItem item = new StudyPlanItem(UUID.randomUUID(), plan.getId(), VALID_TOPIC_1, 30, null);
        studyPlanItemRepository.save(item);

        mockMvc.perform(get("/api/v1/study-plans/student/" + student1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].items", hasSize(1)))
                .andExpect(jsonPath("$.data[0].items[0].topicName").value(VALID_TOPIC_1));
    }

    // -------------------------------------------------------------
    // TEST 7: Öğrenci kendi görevini tamamlandı olarak işaretler -> 200
    // -------------------------------------------------------------
    @Test
    @DisplayName("Ek Doğrulama: Öğrenci kendi görevini tamamlar -> 200, completedAt damgalanır")
    @WithMockUser(username = S1_EMAIL, roles = {"STUDENT"})
    void testCompleteItem_AsSelf_Success() throws Exception {
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(3), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        StudyPlanItem item = new StudyPlanItem(UUID.randomUUID(), plan.getId(), VALID_TOPIC_1, 30, null);
        studyPlanItemRepository.save(item);

        mockMvc.perform(post("/api/v1/study-plans/items/" + item.getId() + "/complete")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(item.getId().toString()))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        StudyPlanItem updatedItem = studyPlanItemRepository.findById(item.getId()).orElseThrow();
        assertNotNull(updatedItem.getCompletedAt());
    }

    // -------------------------------------------------------------
    // TEST 8: T-050D Eşleşmiş öğretmen planı görüldü olarak işaretler -> 200, teacher_seen_at dolar ve idempotenttir
    // -------------------------------------------------------------
    @Test
    @DisplayName("T-050D: Eşleşmiş öğretmen planı görüldü işaretler -> 200, damga basılır ve idempotenttir")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = {"TEACHER"})
    void testMarkPlanAsSeen_AsPairedTeacher_SuccessAndIdempotent() throws Exception {
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        // 1 tamamlanmış, 1 tamamlanmamış kalem
        StudyPlanItem itemCompleted = new StudyPlanItem(UUID.randomUUID(), plan.getId(), VALID_TOPIC_1, 20, Instant.now());
        studyPlanItemRepository.save(itemCompleted);

        StudyPlanItem itemPending = new StudyPlanItem(UUID.randomUUID(), plan.getId(), VALID_TOPIC_2, 30, null);
        studyPlanItemRepository.save(itemPending);

        // İlk görüldü çağrısı
        mockMvc.perform(post("/api/v1/study-plans/" + plan.getId() + "/seen")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        StudyPlanItem updatedCompleted = studyPlanItemRepository.findById(itemCompleted.getId()).orElseThrow();
        assertNotNull(updatedCompleted.getTeacherSeenAt(), "Tamamlanmış kalemde teacher_seen_at dolu olmalıdır.");

        StudyPlanItem updatedPending = studyPlanItemRepository.findById(itemPending.getId()).orElseThrow();
        assertNull(updatedPending.getTeacherSeenAt(), "Tamamlanmamış kalemde teacher_seen_at null kalmalıdır.");

        Instant firstSeenStamp = updatedCompleted.getTeacherSeenAt();

        // İkinci görüldü çağrısı (idempotency - damga değişmez)
        mockMvc.perform(post("/api/v1/study-plans/" + plan.getId() + "/seen")
                        .with(csrf()))
                .andExpect(status().isOk());

        StudyPlanItem secondChecked = studyPlanItemRepository.findById(itemCompleted.getId()).orElseThrow();
        assertEquals(firstSeenStamp, secondChecked.getTeacherSeenAt(), "İkinci görüntülemede damga değişmemelidir.");
    }

    // -------------------------------------------------------------
    // TEST 9: T-050D Eşleşmemiş öğretmen görüldü işaretlemeyi dener -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("T-050D: Eşleşmemiş öğretmen görüldü işaretlemeyi dener -> 403 Forbidden")
    @WithMockUser(username = T_UNPAIRED_EMAIL, roles = {"TEACHER"})
    void testMarkPlanAsSeen_AsUnpairedTeacher_Forbidden() throws Exception {
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        mockMvc.perform(post("/api/v1/study-plans/" + plan.getId() + "/seen")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("yetkiniz yok")));
    }

    // -------------------------------------------------------------
    // TEST 10: T-050D Öğrenci görüldü işaretlemeyi dener -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("T-050D: Öğrenci görüldü işaretlemeyi dener -> 403 Forbidden")
    @WithMockUser(username = S1_EMAIL, roles = {"STUDENT"})
    void testMarkPlanAsSeen_AsStudent_Forbidden() throws Exception {
        StudyPlan plan = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan);
        createdPlanIds.add(plan.getId());

        mockMvc.perform(post("/api/v1/study-plans/" + plan.getId() + "/seen")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------
    // TEST 11: T-050D Öğrenci unseen-count sorgulamayı dener -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("T-050D: Öğrenci unseen-count sorgulamayı dener -> 403 Forbidden")
    @WithMockUser(username = S1_EMAIL, roles = {"STUDENT"})
    void testGetUnseenCount_AsStudent_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/study-plans/unseen-count"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------
    // TEST 12: T-050D Öğretmen unseen-count sorgular -> Yalnızca kendi öğrencileri döner, rozet sıfırlanır ve yeni görevle artar
    // -------------------------------------------------------------
    @Test
    @DisplayName("T-050D: unseen-count yalnızca çağıran öğretmenin kendi öğrencilerini döner ve yabancı öğrenci yer almaz")
    @WithMockUser(username = T_PAIRED_EMAIL, roles = {"TEACHER"})
    void testGetUnseenCount_AsTeacher_ReturnsOnlyOwnStudents_AndResetsOnSeen() throws Exception {
        // Öğrenci 2'yi eşleşmemiş öğretmen ile eşleştir
        membershipService.pairStudentWithTeacher(new PairingRequest(student2.getId(), teacherUnpaired.getId()));

        // Öğrenci 1 için Öğretmen Paired tarafından plan aç ve 1 görev tamamlat (unseen)
        StudyPlan plan1 = new StudyPlan(UUID.randomUUID(), student1.getId(), teacherPaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan1);
        createdPlanIds.add(plan1.getId());

        StudyPlanItem item1 = new StudyPlanItem(UUID.randomUUID(), plan1.getId(), VALID_TOPIC_1, 20, Instant.now());
        studyPlanItemRepository.save(item1);

        // Öğrenci 2 için Öğretmen Unpaired tarafından plan aç ve 1 görev tamamlat (unseen)
        StudyPlan plan2 = new StudyPlan(UUID.randomUUID(), student2.getId(), teacherUnpaired.getId(), LocalDate.now().plusDays(5), Instant.now());
        studyPlanRepository.save(plan2);
        createdPlanIds.add(plan2.getId());

        StudyPlanItem item2 = new StudyPlanItem(UUID.randomUUID(), plan2.getId(), VALID_TOPIC_1, 30, Instant.now());
        studyPlanItemRepository.save(item2);

        // Öğretmen Paired olarak unseen-count sorgula
        mockMvc.perform(get("/api/v1/study-plans/unseen-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data." + student1.getId()).value(1))
                .andExpect(jsonPath("$.data." + student2.getId()).doesNotExist()); // Başka öğretmenin öğrencisi YER ALMAZ!

        // Görüldü yap -> rozet 0'a düşmeli
        mockMvc.perform(post("/api/v1/study-plans/" + plan1.getId() + "/seen")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/study-plans/unseen-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data." + student1.getId()).value(0));

        // Yeni bir kalem daha tamamlanınca rozet yeniden 1 olmalı
        StudyPlanItem item1New = new StudyPlanItem(UUID.randomUUID(), plan1.getId(), VALID_TOPIC_2, 15, Instant.now());
        studyPlanItemRepository.save(item1New);

        mockMvc.perform(get("/api/v1/study-plans/unseen-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data." + student1.getId()).value(1));
    }
}
