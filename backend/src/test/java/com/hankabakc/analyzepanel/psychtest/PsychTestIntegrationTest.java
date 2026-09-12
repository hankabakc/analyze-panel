package com.hankabakc.analyzepanel.psychtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestAnswerItemDto;
import com.hankabakc.analyzepanel.psychtest.dto.SubmitPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestAssignment;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestResponse;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestStatus;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestAssignmentRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestResponseRepository;
import com.hankabakc.analyzepanel.psychtest.service.StaiScaleDefinition;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PsychTestIntegrationTest: T-052A Psikolojik ölçek (STAI) atama, doldurma, puanlama ve yetki kontrollerini test eder.
 */
@SpringBootTest
public class PsychTestIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PsychTestAssignmentRepository assignmentRepository;

    @Autowired
    private PsychTestResponseRepository responseRepository;

    @Autowired
    private MembershipService membershipService;

    private AppUser managerUser;
    private AppUser teacherUser;
    private AppUser otherTeacherUser;
    private AppUser studentUser;
    private AppUser otherStudentUser;

    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Yönetici
        managerUser = createUniqueUser("psych_mgr_", UserRole.MANAGER);
        // 2. Eşleşmiş Öğretmen
        teacherUser = createUniqueUser("psych_tch1_", UserRole.TEACHER);
        // 3. Eşleşmemiş Öğretmen
        otherTeacherUser = createUniqueUser("psych_tch2_", UserRole.TEACHER);
        // 4. Öğrenci
        studentUser = createUniqueUser("psych_std1_", UserRole.STUDENT);
        // 5. Başka Öğrenci
        otherStudentUser = createUniqueUser("psych_std2_", UserRole.STUDENT);

        // Öğretmen - Öğrenci eşleşmesi
        membershipService.pairStudentWithTeacher(new PairingRequest(studentUser.getId(), teacherUser.getId()));
    }

    @AfterEach
    void tearDown() {
        // T-060 (g): Yalnızca bu test oturumunda oluşturulan kullanıcıların atamaları ve yanıtları temizlenir.
        // Geliştirme veritabanındaki diğer kullanıcıların testleri silinmez.
        List<PsychTestAssignment> testAssignments = assignmentRepository.findAllByStudentIdInOrderByAssignedAtDesc(createdUserIds);
        List<UUID> testAssignmentIds = testAssignments.stream().map(PsychTestAssignment::getId).toList();
        if (!testAssignmentIds.isEmpty()) {
            List<PsychTestResponse> responses = responseRepository.findAllByAssignmentIdIn(testAssignmentIds);
            responseRepository.deleteAll(responses);
            assignmentRepository.deleteAll(testAssignments);
        }

        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
    }

    private AppUser createUniqueUser(String prefix, UserRole role) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = new AppUser(
                prefix + uniqueSuffix + "@test.local",
                prefix + uniqueSuffix,
                role,
                UserStatus.ACTIVE,
                role == UserRole.STUDENT ? 8 : null,
                "$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFvX4e26tA7l9aI0M1E5jJ5m4m5a5m5a",
                false
        );
        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Yönetici öğrenciye STAI testi atayabilir")
    void testAssignTest_AsManager_Success() throws Exception {
        AssignPsychTestRequest request = new AssignPsychTestRequest("STAI", List.of(studentUser.getId()));

        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(managerUser.getEmail()).roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignedStudentIds", hasSize(1)))
                .andExpect(jsonPath("$.data.assignedStudentIds[0]").value(studentUser.getId().toString()))
                .andExpect(jsonPath("$.data.skippedStudentIds", hasSize(0)));

        assertTrue(assignmentRepository.existsByStudentIdAndTestCodeAndStatus(studentUser.getId(), "STAI", PsychTestStatus.PENDING));
    }

    @Test
    @DisplayName("Açık atama varken tekrar atama yeni kayıt oluşturmaz (skippedStudentIds listesine eklenir)")
    void testAssignTest_ExistingPending_Skipped() throws Exception {
        // İlk atama
        PsychTestAssignment first = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignmentRepository.save(first);

        long countBefore = assignmentRepository.count();

        AssignPsychTestRequest request = new AssignPsychTestRequest("STAI", List.of(studentUser.getId()));

        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(managerUser.getEmail()).roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignedStudentIds", hasSize(0)))
                .andExpect(jsonPath("$.data.skippedStudentIds", hasSize(1)))
                .andExpect(jsonPath("$.data.skippedStudentIds[0]").value(studentUser.getId().toString()));

        long countAfter = assignmentRepository.count();
        assertEquals(countBefore, countAfter, "Yeni kayıt açılmamalıdır");
    }

    @Test
    @DisplayName("403 Yetki: Öğretmen test ataması yapamaz")
    void testAssignTest_AsTeacher_Forbidden() throws Exception {
        AssignPsychTestRequest request = new AssignPsychTestRequest("STAI", List.of(studentUser.getId()));

        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(teacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("403 Yetki: Öğrenci test ataması yapamaz")
    void testAssignTest_AsStudent_Forbidden() throws Exception {
        AssignPsychTestRequest request = new AssignPsychTestRequest("STAI", List.of(studentUser.getId()));

        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Öğrenci my-assignments çağırabilir ve puan alanları DÖNMEZ (APP-03 §4)")
    void testGetMyAssignments_AsStudent_NoScoresInResponse() throws Exception {
        // Hem bekleyen hem tamamlanmış (puanlı) atama ekle
        PsychTestAssignment a1 = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        PsychTestAssignment a2 = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now().minusSeconds(3600),
                PsychTestStatus.COMPLETED, Instant.now().minusSeconds(1800), 55, 62
        );
        assignmentRepository.saveAll(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/psych-tests/my-assignments")
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].stateScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].traitScore").doesNotExist())
                .andExpect(jsonPath("$.data[1].stateScore").doesNotExist())
                .andExpect(jsonPath("$.data[1].traitScore").doesNotExist());
    }

    @Test
    @DisplayName("Puanlama Uç Değer (a): Doğrudan maddeler 4, Ters maddeler 1 -> Durumluk: 80, Sürekli: 80")
    void testScoring_AllDirect4_AllReverse1_Yields80And80() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            int val = StaiScaleDefinition.STATE_REVERSE_ITEMS.contains(i) ? 1 : 4;
            answers.add(new PsychTestAnswerItemDto(i, val));
        }
        for (int i = 21; i <= 40; i++) {
            int val = StaiScaleDefinition.TRAIT_REVERSE_ITEMS.contains(i) ? 1 : 4;
            answers.add(new PsychTestAnswerItemDto(i, val));
        }

        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.stateScore").doesNotExist())
                .andExpect(jsonPath("$.data.traitScore").doesNotExist());

        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(80, updated.getStateScore(), "Durumluk puanı 80 olmalıdır");
        assertEquals(80, updated.getTraitScore(), "Sürekli puanı 80 olmalıdır");
    }

    @Test
    @DisplayName("Puanlama Uç Değer (b): Doğrudan maddeler 1, Ters maddeler 4 -> Durumluk: 20, Sürekli: 20")
    void testScoring_AllDirect1_AllReverse4_Yields20And20() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            int val = StaiScaleDefinition.STATE_REVERSE_ITEMS.contains(i) ? 4 : 1;
            answers.add(new PsychTestAnswerItemDto(i, val));
        }
        for (int i = 21; i <= 40; i++) {
            int val = StaiScaleDefinition.TRAIT_REVERSE_ITEMS.contains(i) ? 4 : 1;
            answers.add(new PsychTestAnswerItemDto(i, val));
        }

        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(20, updated.getStateScore(), "Durumluk puanı 20 olmalıdır");
        assertEquals(20, updated.getTraitScore(), "Sürekli puanı 20 olmalıdır");
    }

    @Test
    @DisplayName("Puanlama Örnek (c): Tüm 40 madde 4 -> Durumluk: 50, Sürekli: 59")
    void testScoring_AllItems4_Yields50And59() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            answers.add(new PsychTestAnswerItemDto(i, 4));
        }

        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(50, updated.getStateScore(), "Durumluk puanı 50 olmalıdır");
        assertEquals(59, updated.getTraitScore(), "Sürekli puanı 59 olmalıdır (13*4 - 7*4 + 35 = 59)");
    }

    @Test
    @DisplayName("403 Yetki: Başka öğrencinin test atamasını doldurma denemesi engellenir (ENG-11 IDOR)")
    void testSubmitTest_OtherStudentAssignment_Forbidden() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            answers.add(new PsychTestAnswerItemDto(i, 2));
        }
        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        // otherStudentUser doldurmayı dener
        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(otherStudentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Eksik cevapla gönderim 400 döner (39 madde)")
    void testSubmitTest_MissingAnswers_BadRequest() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(), PsychTestStatus.PENDING
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 39; i++) { // 40. madde eksik
            answers.add(new PsychTestAnswerItemDto(i, 2));
        }
        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("İkinci kez doldurma denemesi reddedilir ve ilk puanlar korunur")
    void testSubmitTest_SecondTime_Rejected() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now().minusSeconds(100),
                PsychTestStatus.COMPLETED, Instant.now().minusSeconds(50), 45, 48
        );
        assignment = assignmentRepository.save(assignment);

        List<PsychTestAnswerItemDto> answers = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            answers.add(new PsychTestAnswerItemDto(i, 4));
        }
        SubmitPsychTestRequest request = new SubmitPsychTestRequest(answers);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        PsychTestAssignment after = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(45, after.getStateScore());
        assertEquals(48, after.getTraitScore());
    }

    @Test
    @DisplayName("403 Yetki: Öğrenci sonuç okuma ucu çağıramaz (APP-03 §4)")
    void testGetStudentResults_AsStudent_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("403 Yetki: Eşleşmemiş öğretmen sonuç okuma ucu çağıramaz (APP-03 §3)")
    void testGetStudentResults_AsUnpairedTeacher_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(otherTeacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Eşleşmiş öğretmen öğrencisinin sonucunu puanları ve hesaplanmış seviyeleriyle okuyabilir (T-061)")
    void testGetStudentResults_AsPairedTeacher_Success() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(),
                PsychTestStatus.COMPLETED, Instant.now(), 52, 60
        );
        assignmentRepository.save(assignment);

        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].stateScore").value(52))
                .andExpect(jsonPath("$.data[0].traitScore").value(60))
                .andExpect(jsonPath("$.data[0].stateLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data[0].traitLevel").value("HIGH"));
    }

    @Test
    @DisplayName("Yönetici tüm sonuçları puanları ve hesaplanmış seviyeleriyle listeleyebilir (T-061)")
    void testGetAllResults_AsManager_Success() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(),
                PsychTestStatus.COMPLETED, Instant.now(), 40, 42
        );
        assignmentRepository.save(assignment);

        mockMvc.perform(get("/api/v1/psych-tests/results")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].stateScore").value(40))
                .andExpect(jsonPath("$.data[0].traitScore").value(42))
                .andExpect(jsonPath("$.data[0].stateLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data[0].traitLevel").value("MEDIUM"));
    }

    @Test
    @DisplayName("Tamamlanmamış (PENDING) testte seviye alanları null döner (T-061)")
    void testGetResults_PendingTest_HasNullLevels() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(),
                PsychTestStatus.PENDING
        );
        assignmentRepository.save(assignment);

        mockMvc.perform(get("/api/v1/psych-tests/results")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].stateScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].traitScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].stateLevel").doesNotExist())
                .andExpect(jsonPath("$.data[0].traitLevel").doesNotExist());
    }

    @Test
    @DisplayName("Öğrencinin kendi test listesinde puan ve seviye alanları asla yer almaz (APP-03 §4, T-061)")
    void testGetMyAssignments_AsStudent_DoesNotContainScoresOrLevels() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(), "STAI", studentUser.getId(), managerUser.getId(), Instant.now(),
                PsychTestStatus.COMPLETED, Instant.now(), 55, 65
        );
        assignmentRepository.save(assignment);

        mockMvc.perform(get("/api/v1/psych-tests/my-assignments")
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].stateScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].traitScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].stateLevel").doesNotExist())
                .andExpect(jsonPath("$.data[0].traitLevel").doesNotExist());
    }
}
