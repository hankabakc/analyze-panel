package com.hankabakc.analyzepanel.psychtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonMarkedCell;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonSubmissionRequest;
import com.hankabakc.analyzepanel.psychtest.entity.BourdonResponse;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestAssignment;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestStatus;
import com.hankabakc.analyzepanel.psychtest.repository.BourdonResponseRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestAssignmentRepository;
import com.hankabakc.analyzepanel.psychtest.service.BourdonScaleDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * BourdonTestIntegrationTest: T-053A Bourdon dikkat testi ızgara tanımı, süre doğrulaması,
 * puanlama motoru ve güvenlik/yetkilendirme kontrollerini test eder.
 */
@SpringBootTest
public class BourdonTestIntegrationTest {

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
    private BourdonResponseRepository bourdonResponseRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private BourdonScaleDefinition bourdonScaleDefinition;

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

        managerUser = createUniqueUser("bourdon_mgr_", UserRole.MANAGER);
        teacherUser = createUniqueUser("bourdon_tch1_", UserRole.TEACHER);
        otherTeacherUser = createUniqueUser("bourdon_tch2_", UserRole.TEACHER);
        studentUser = createUniqueUser("bourdon_std1_", UserRole.STUDENT);
        otherStudentUser = createUniqueUser("bourdon_std2_", UserRole.STUDENT);

        membershipService.pairStudentWithTeacher(new PairingRequest(studentUser.getId(), teacherUser.getId()));
    }

    @AfterEach
    void tearDown() {
        // T-060 (g): Yalnızca bu test oturumunda oluşturulan kullanıcıların atamaları ve yanıtları temizlenir.
        // Geliştirme veritabanındaki diğer kullanıcıların testleri silinmez.
        List<PsychTestAssignment> testAssignments = assignmentRepository.findAllByStudentIdInOrderByAssignedAtDesc(createdUserIds);
        List<UUID> testAssignmentIds = testAssignments.stream().map(PsychTestAssignment::getId).toList();
        if (!testAssignmentIds.isEmpty()) {
            List<BourdonResponse> responses = bourdonResponseRepository.findAllByAssignmentIdIn(testAssignmentIds);
            bourdonResponseRepository.deleteAll(responses);
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
        AppUser u = new AppUser(
                prefix + uniqueSuffix + "@test.local",
                prefix + uniqueSuffix,
                role,
                UserStatus.ACTIVE,
                role == UserRole.STUDENT ? 8 : null,
                "$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFvX4e26tA7l9aI0M1E5jJ5m4m5a5m5a",
                false
        );
        AppUser saved = userRepository.save(u);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Kriter 2: Kod içindeki ızgaranın blok bazlı harf sayıları S-021 kararı uyarınca 11/10/10/10, 10/9/10/9, 10/11/9/10 (toplam 119) olmalıdır")
    void testGridLetterCounts() {
        List<String> grid = bourdonScaleDefinition.getGridRows();
        assertEquals(30, grid.size(), "Izgara tam 30 satır olmalıdır.");

        for (int r = 0; r < 30; r++) {
            assertEquals(22, grid.get(r).length(), "Satır " + (r + 1) + " tam 22 harf olmalıdır.");
        }

        // Blok 1 (Satırlar 0-9)
        int[] b1 = countBlockTargets(grid, 0, 10);
        assertEquals(11, b1[0], "Blok 1 'a' sayısı 11 olmalıdır.");
        assertEquals(10, b1[1], "Blok 1 'b' sayısı 10 olmalıdır.");
        assertEquals(10, b1[2], "Blok 1 'd' sayısı 10 olmalıdır.");
        assertEquals(10, b1[3], "Blok 1 'g' sayısı 10 olmalıdır.");
        assertEquals(41, b1[0] + b1[1] + b1[2] + b1[3], "Blok 1 toplam hedef sayısı 41 olmalıdır.");

        // Blok 2 (Satırlar 10-19)
        int[] b2 = countBlockTargets(grid, 10, 20);
        assertEquals(10, b2[0], "Blok 2 'a' sayısı 10 olmalıdır.");
        assertEquals(9, b2[1], "Blok 2 'b' sayısı 9 olmalıdır.");
        assertEquals(10, b2[2], "Blok 2 'd' sayısı 10 olmalıdır.");
        assertEquals(9, b2[3], "Blok 2 'g' sayısı 9 olmalıdır.");
        assertEquals(38, b2[0] + b2[1] + b2[2] + b2[3], "Blok 2 toplam hedef sayısı 38 olmalıdır.");

        // Blok 3 (Satırlar 20-29)
        int[] b3 = countBlockTargets(grid, 20, 30);
        assertEquals(10, b3[0], "Blok 3 'a' sayısı 10 olmalıdır.");
        assertEquals(11, b3[1], "Blok 3 'b' sayısı 11 olmalıdır.");
        assertEquals(9, b3[2], "Blok 3 'd' sayısı 9 olmalıdır.");
        assertEquals(10, b3[3], "Blok 3 'g' sayısı 10 olmalıdır.");
        assertEquals(40, b3[0] + b3[1] + b3[2] + b3[3], "Blok 3 toplam hedef sayısı 40 olmalıdır.");

        // Toplam Hedef
        int totalTargets = (b1[0] + b1[1] + b1[2] + b1[3]) +
                           (b2[0] + b2[1] + b2[2] + b2[3]) +
                           (b3[0] + b3[1] + b3[2] + b3[3]);
        assertEquals(119, totalTargets, "Genel toplam hedef harf sayısı 119 olmalıdır.");
    }

    private int[] countBlockTargets(List<String> grid, int startRow, int endRow) {
        int a = 0, b = 0, d = 0, g = 0;
        for (int r = startRow; r < endRow; r++) {
            String rowStr = grid.get(r);
            for (char ch : rowStr.toCharArray()) {
                if (ch == 'a') a++;
                else if (ch == 'b') b++;
                else if (ch == 'd') d++;
                else if (ch == 'g') g++;
            }
        }
        return new int[]{a, b, d, g};
    }

    @Test
    @DisplayName("Yönetici Bourdon testini öğrenciye atayabilmeli")
    void testAssignBourdonTest() throws Exception {
        AssignPsychTestRequest request = new AssignPsychTestRequest(
                BourdonScaleDefinition.TEST_CODE,
                List.of(studentUser.getId())
        );

        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(managerUser.getEmail()).roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignedStudentIds", hasSize(1)))
                .andExpect(jsonPath("$.data.skippedStudentIds", hasSize(0)));

        List<PsychTestAssignment> assignments = assignmentRepository.findAllByStudentIdOrderByAssignedAtDesc(studentUser.getId());
        assertEquals(1, assignments.size());
        assertEquals("BOURDON", assignments.get(0).getTestCode());
        assertEquals(PsychTestStatus.PENDING, assignments.get(0).getStatus());
    }

    @Test
    @DisplayName("Kriter 4: Öğrenci testi başlattığında sunucu started_at basmalı, durum IN_PROGRESS olmalı ve ızgara dönmelidir")
    void testStartBourdonTest_SetsStartedAtAndReturnsGrid() throws Exception {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(),
                BourdonScaleDefinition.TEST_CODE,
                studentUser.getId(),
                managerUser.getId(),
                Instant.now(),
                PsychTestStatus.PENDING
        );
        assignmentRepository.save(assignment);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/start")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(assignment.getId().toString()))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.durationSeconds").value(180))
                .andExpect(jsonPath("$.data.grid", hasSize(30)));

        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(PsychTestStatus.IN_PROGRESS, updated.getStatus());
        assertNotNull(updated.getStartedAt());
    }

    @Test
    @DisplayName("T-053F / APP-01 §2.1: Devam eden (IN_PROGRESS) atamada start çağrıldığında aynı started_at ve ızgara dönmeli, saat sıfırlanmamalıdır")
    void testStartBourdonTest_AlreadyInProgress_ReturnsSameStartedAtAndGrid() throws Exception {
        Instant originalStartedAt = Instant.now().minusSeconds(45).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), originalStartedAt);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/start")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(assignment.getId().toString()))
                .andExpect(jsonPath("$.data.startedAt").value(originalStartedAt.toString()))
                .andExpect(jsonPath("$.data.durationSeconds").value(180))
                .andExpect(jsonPath("$.data.grid", hasSize(30)));

        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(PsychTestStatus.IN_PROGRESS, updated.getStatus());
        assertEquals(originalStartedAt.toEpochMilli(), updated.getStartedAt().toEpochMilli());
    }

    @Test
    @DisplayName("Kriter 3a: Tüm hedefler işaretlendiğinde doğru = 119, atlama = 0, yanlış = 0 olmalıdır")
    void testSubmitBourdon_FullCorrect() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(60));

        // Tüm 119 hedef koordinatı topla
        List<BourdonMarkedCell> allTargets = new ArrayList<>();
        List<String> grid = bourdonScaleDefinition.getGridRows();
        for (int r = 0; r < 30; r++) {
            for (int c = 0; c < 22; c++) {
                if (BourdonScaleDefinition.isTargetChar(grid.get(r).charAt(c))) {
                    allTargets.add(new BourdonMarkedCell(r, c));
                }
            }
        }
        assertEquals(119, allTargets.size());

        BourdonSubmissionRequest request = new BourdonSubmissionRequest(allTargets);

        // Teslim isteği (öğrenciye puan dönülmez - Kriter 6)
        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                // Öğrenciye puan alanları dönülmediği doğrulanır
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.totalCorrect").doesNotExist())
                .andExpect(jsonPath("$.data.correct").doesNotExist());

        // Öğretmen sonuçları okur ve puanları doğrular
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timedOut").value(false))
                .andExpect(jsonPath("$.data.totalCorrect").value(119))
                .andExpect(jsonPath("$.data.totalOmitted").value(0))
                .andExpect(jsonPath("$.data.totalIncorrect").value(0))
                .andExpect(jsonPath("$.data.totalTargets").value(119))
                .andExpect(jsonPath("$.data.block1.correct").value(41))
                .andExpect(jsonPath("$.data.block1.omitted").value(0))
                .andExpect(jsonPath("$.data.block1.incorrect").value(0))
                .andExpect(jsonPath("$.data.block2.correct").value(38))
                .andExpect(jsonPath("$.data.block2.omitted").value(0))
                .andExpect(jsonPath("$.data.block2.incorrect").value(0))
                .andExpect(jsonPath("$.data.block3.correct").value(40))
                .andExpect(jsonPath("$.data.block3.omitted").value(0))
                .andExpect(jsonPath("$.data.block3.incorrect").value(0));
    }

    @Test
    @DisplayName("Kriter 3b: Hiçbir hedef işaretlenmediğinde doğru = 0, atlama = 119, yanlış = 0 olmalıdır")
    void testSubmitBourdon_NoneMarked() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(50));

        BourdonSubmissionRequest request = new BourdonSubmissionRequest(List.of());

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCorrect").value(0))
                .andExpect(jsonPath("$.data.totalOmitted").value(119))
                .andExpect(jsonPath("$.data.totalIncorrect").value(0));
    }

    @Test
    @DisplayName("Kriter 3c: Hedef olmayan hücreler işaretlendiğinde yanlış sayısı artmalıdır")
    void testSubmitBourdon_WithIncorrectMarks() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(40));

        List<BourdonMarkedCell> cells = new ArrayList<>();
        // 5 doğru hedef
        cells.add(new BourdonMarkedCell(0, 0)); // 'a' (doğru)
        cells.add(new BourdonMarkedCell(0, 8)); // 'a' (doğru)
        cells.add(new BourdonMarkedCell(0, 13)); // 'a' (doğru)
        cells.add(new BourdonMarkedCell(0, 16)); // 'b' (doğru)
        cells.add(new BourdonMarkedCell(1, 2)); // 'b' (doğru)

        // 2 yanlış işaretleme (hedef olmayan harfler)
        cells.add(new BourdonMarkedCell(0, 1)); // 'e' (yanlış!)
        cells.add(new BourdonMarkedCell(0, 2)); // 'p' (yanlış!)

        BourdonSubmissionRequest request = new BourdonSubmissionRequest(cells);

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCorrect").value(5))
                .andExpect(jsonPath("$.data.totalIncorrect").value(2))
                .andExpect(jsonPath("$.data.totalOmitted").value(114)); // 119 - 5 = 114
    }

    @Test
    @DisplayName("T-053D / APP-01 §2.1: Süre aşımı durumunda (3 dakikadan eski teslim) teslim kabul edilmeli (200 OK), timed_out=true ve status=COMPLETED olmalıdır")
    void testSubmitBourdon_TimeExceeded_AcceptedAsTimedOut() throws Exception {
        // started_at 300 saniye (5 dakika) önce
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(300));

        BourdonSubmissionRequest request = new BourdonSubmissionRequest(List.of(new BourdonMarkedCell(0, 0)));

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Veritabanında atamanın durumu COMPLETED olmalı
        PsychTestAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertEquals(PsychTestStatus.COMPLETED, updated.getStatus());

        // BourdonResponse kaydında timed_out=true ve durationSeconds >= 300 olmalı
        var bourdonResp = bourdonResponseRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertTrue(bourdonResp.isTimedOut());
        assertTrue(bourdonResp.getDurationSeconds() >= 300);

        // Sonuç sorgusunda timedOut=true dönmeli
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timedOut").value(true))
                .andExpect(jsonPath("$.data.durationSeconds", greaterThanOrEqualTo(300)));
    }

    @Test
    @DisplayName("Kriter 5: 403 Yetki Kontrolleri (IDOR, rol kısıtlamaları ve öğrenci puan gizliliği)")
    void testSecurity_403Forbiddens() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(30));

        // 1. Öğretmenin test atama denemesi -> 403
        mockMvc.perform(post("/api/v1/psych-tests/assignments")
                        .with(csrf())
                        .with(user(teacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignPsychTestRequest("BOURDON", List.of(studentUser.getId())))))
                .andExpect(status().isForbidden());

        // 2. Başka bir öğrencinin başkasının testini başlatma denemesi -> 403
        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/start")
                        .with(csrf())
                        .with(user(otherStudentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // 3. Başka bir öğrencinin başkasının testini teslim etme denemesi -> 403
        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(otherStudentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BourdonSubmissionRequest(List.of()))))
                .andExpect(status().isForbidden());

        // 4. Öğrencinin sonuç görüntüleme denemesi -> 403 (APP-03 §4)
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // 5. Eşleşmemiş öğretmenin sonuç görüntüleme denemesi -> 403
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(otherTeacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-053C: Yetkili öğretmen ve yönetici Bourdon sonucunu nötr gözlem notuyla görebilir; eşleşmeyen öğretmen ve öğrenci 403 alır")
    void testBourdonResult_AuthorizedTeacherAndManager_WithObservationNote() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(120));

        // 3 hedef harf işaretleyelim (row 0, row 1, row 2'den)
        List<BourdonMarkedCell> marked = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 22; c++) {
                if (bourdonScaleDefinition.isTargetAt(r, c)) {
                    marked.add(new BourdonMarkedCell(r, c));
                    break;
                }
            }
        }

        // Öğrenci teslim eder
        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BourdonSubmissionRequest(marked))))
                .andExpect(status().isOk());

        // 1. Eşleşen öğretmen sonucu görür (200 OK)
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCorrect").value(3))
                .andExpect(jsonPath("$.data.block1.correct").value(3))
                .andExpect(jsonPath("$.data.timedOut").value(false))
                .andExpect(jsonPath("$.data.observationNote").isNotEmpty());

        // 2. Yönetici sonucu görür (200 OK)
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCorrect").value(3));

        // 3. Eşleşmeyen öğretmen 403 alır
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(otherTeacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());

        // 4. Öğrenci 403 alır (APP-03 §4)
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // 5. Öğrenci listeleme ucundan da 403 alır
        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // 6. Eşleşen öğretmen öğrenci sonuç listesini sorgular -> Bourdon özet alanları dolu döner
        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bourdonTotalCorrect").value(3))
                .andExpect(jsonPath("$.data[0].bourdonTimedOut").value(false));

        // 7. Yönetici tüm sonuçları sorgular -> Bourdon özet alanları dolu döner
        mockMvc.perform(get("/api/v1/psych-tests/results")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bourdonTotalCorrect").value(3));
    }

    @Test
    @DisplayName("T-060 (f): Bourdon gözlem notu - 10/119 işaretleme durumunda 'beklenenin altında' notu döner")
    void testBourdonObservationNote_LowMarkedCount_ReturnsUnderExpected() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(100));

        // 7 doğru hedef ve 3 yanlış hedef (toplam 10 işaretleme / ~119 hedef)
        List<BourdonMarkedCell> marked = new ArrayList<>();
        int correctCount = 0;
        int wrongCount = 0;

        for (int r = 0; r < 30; r++) {
            for (int c = 0; c < 22; c++) {
                if (bourdonScaleDefinition.isTargetAt(r, c) && correctCount < 7) {
                    marked.add(new BourdonMarkedCell(r, c));
                    correctCount++;
                } else if (!bourdonScaleDefinition.isTargetAt(r, c) && wrongCount < 3) {
                    marked.add(new BourdonMarkedCell(r, c));
                    wrongCount++;
                }
            }
            if (correctCount >= 7 && wrongCount >= 3) break;
        }

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BourdonSubmissionRequest(marked))))
                .andExpect(status().isOk());

        // Öğretmen sorgular
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCorrect").value(7))
                .andExpect(jsonPath("$.data.totalIncorrect").value(3))
                .andExpect(jsonPath("$.data.observationNote").value("İşaretlenen hücre sayısı beklenenin altındadır."));
    }

    @Test
    @DisplayName("T-060 (f): Bourdon gözlem notu - Tüm hedefler işaretli ve hatasız olduğunda 'dengeli' notu döner")
    void testBourdonObservationNote_BalancedAllTargets_ReturnsBalanced() throws Exception {
        PsychTestAssignment assignment = createInProgressAssignment(studentUser.getId(), Instant.now().minusSeconds(100));

        // Bütün hedef harfleri hatasız işaretleyelim
        List<BourdonMarkedCell> marked = new ArrayList<>();
        for (int r = 0; r < 30; r++) {
            for (int c = 0; c < 22; c++) {
                if (bourdonScaleDefinition.isTargetAt(r, c)) {
                    marked.add(new BourdonMarkedCell(r, c));
                }
            }
        }

        mockMvc.perform(post("/api/v1/psych-tests/assignments/" + assignment.getId() + "/submit-bourdon")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BourdonSubmissionRequest(marked))))
                .andExpect(status().isOk());

        // Öğretmen sorgular
        mockMvc.perform(get("/api/v1/psych-tests/assignments/" + assignment.getId() + "/bourdon-result")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOmitted").value(0))
                .andExpect(jsonPath("$.data.totalIncorrect").value(0))
                .andExpect(jsonPath("$.data.observationNote").value("Bölümler arasında dengeli bir işaretleme dağılımı gözlenmiştir."));
    }

    private PsychTestAssignment createInProgressAssignment(UUID studentId, Instant startedAt) {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(),
                BourdonScaleDefinition.TEST_CODE,
                studentId,
                managerUser.getId(),
                Instant.now(),
                PsychTestStatus.IN_PROGRESS
        );
        assignment.setStartedAt(startedAt);
        return assignmentRepository.save(assignment);
    }
}
