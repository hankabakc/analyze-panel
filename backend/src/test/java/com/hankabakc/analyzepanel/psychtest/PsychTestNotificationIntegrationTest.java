package com.hankabakc.analyzepanel.psychtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.psychtest.dto.MarkPsychResultsSeenRequest;
import com.hankabakc.analyzepanel.psychtest.entity.PsychResultView;
import com.hankabakc.analyzepanel.psychtest.entity.PsychResultViewId;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestAssignment;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestStatus;
import com.hankabakc.analyzepanel.psychtest.repository.PsychResultViewRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestAssignmentRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestResponseRepository;
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
import java.util.List;
import java.util.Optional;
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
 * PsychTestNotificationIntegrationTest: T-062 Psikolojik test tamamlanma bildirimi
 * ("yeni sonuç" işareti / görüldü izleme) entegrasyon testleri.
 * 
 * Kabul Kriterleri (T-062):
 * (1) Öğrenci testi bitirince eşleşmiş öğretmenin ve yöneticinin sayısı 1 artar, eşleşmemiş öğretmeninki artmaz.
 * (2) Öğretmen "görüldü" deyince onun sayısı 0 olur, yöneticininki 1 kalır.
 * (3) İkinci "görüldü" çağrısı damgayı değiştirmez (ON CONFLICT DO NOTHING).
 * (4) Eşleşmemiş öğretmenin "görüldü" çağrısı 403, öğrencinin sayım ve "görüldü" çağrıları 403.
 * (5) Sonuç listeleme uçlarını çağırmak sayıyı değiştirmez (ENG-07 §1.1).
 *
 * Yöneticinin toplam sayısı sistem genelidir ve geliştirme verisiyle değişir; bu yüzden yönetici için
 * yalnızca testin kendi öğrencisinin sayısı doğrulanır (T-063A engel düzeltmesi).
 */
@SpringBootTest
public class PsychTestNotificationIntegrationTest {

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
    private PsychResultViewRepository resultViewRepository;

    @Autowired
    private MembershipService membershipService;

    private AppUser managerUser;
    private AppUser teacherUser;
    private AppUser unpairedTeacherUser;
    private AppUser studentUser;

    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdAssignmentIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Yönetici
        managerUser = createUniqueUser("notif_mgr_", UserRole.MANAGER);
        // 2. Eşleşmiş Öğretmen
        teacherUser = createUniqueUser("notif_tch1_", UserRole.TEACHER);
        // 3. Eşleşmemiş Öğretmen
        unpairedTeacherUser = createUniqueUser("notif_tch2_", UserRole.TEACHER);
        // 4. Öğrenci
        studentUser = createUniqueUser("notif_std1_", UserRole.STUDENT);

        // Öğretmen - Öğrenci eşleşmesi
        membershipService.pairStudentWithTeacher(new PairingRequest(studentUser.getId(), teacherUser.getId()));
    }

    @AfterEach
    void tearDown() {
        // İlgili atamaların görüldü kayıtlarını temizle
        if (!createdAssignmentIds.isEmpty()) {
            for (UUID aId : createdAssignmentIds) {
                try {
                    resultViewRepository.deleteById(new PsychResultViewId(aId, teacherUser.getId()));
                } catch (Exception ignored) {}
                try {
                    resultViewRepository.deleteById(new PsychResultViewId(aId, managerUser.getId()));
                } catch (Exception ignored) {}
            }
        }

        // Test atamalarını ve yanıtlarını temizle
        List<PsychTestAssignment> testAssignments = assignmentRepository.findAllByStudentIdInOrderByAssignedAtDesc(createdUserIds);
        List<UUID> testAssignmentIds = testAssignments.stream().map(PsychTestAssignment::getId).toList();
        if (!testAssignmentIds.isEmpty()) {
            responseRepository.deleteAll(responseRepository.findAllByAssignmentIdIn(testAssignmentIds));
            assignmentRepository.deleteAll(testAssignments);
        }

        // Test kullanıcılarını sil (ilişkili pairings cascade/servis ile kalkar)
        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
        createdAssignmentIds.clear();
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

    private PsychTestAssignment createCompletedAssignment(AppUser student, AppUser assignedBy, String testCode) {
        PsychTestAssignment assignment = new PsychTestAssignment(
                UUID.randomUUID(),
                testCode,
                student.getId(),
                assignedBy.getId(),
                Instant.now().minusSeconds(3600),
                PsychTestStatus.COMPLETED,
                Instant.now(),
                45,
                50
        );
        PsychTestAssignment saved = assignmentRepository.save(assignment);
        createdAssignmentIds.add(saved.getId());
        return saved;
    }

    /**
     * Kriter 1: Öğrenci testi bitirince eşleşmiş öğretmenin ve yöneticinin sayısı 1 artar,
     * eşleşmemiş öğretmeninki artmaz.
     */
    @Test
    @DisplayName("Kriter 1: Tamamlanan test eşleşmiş öğretmen ve yöneticide görülmemiş olarak sayılır, eşleşmemiş öğretmende sayılmaz")
    void testUnseenCountIncreasesOnCompletion() throws Exception {
        // Başlangıçta test yok -> eşleşmiş öğretmen 0, yönetici 0, eşleşmemiş 0
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnseen", is(0)));

        // Tamamlanmış STAI testi oluştur
        createCompletedAssignment(studentUser, managerUser, "STAI");

        // 1. Eşleşmiş öğretmen: totalUnseen = 1, studentCounts[studentUser.id] = 1
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnseen", is(1)))
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));

        // 2. Yönetici: totalUnseen = 1, studentCounts[studentUser.id] = 1
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));

        // 3. Eşleşmemiş öğretmen: totalUnseen = 0 (bu öğrenci ile eşleşmesi yok)
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(unpairedTeacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnseen", is(0)));
    }

    /**
     * Kriter 2: Öğretmen "görüldü" deyince onun sayısı 0 olur, yöneticininki 1 kalır.
     * Kişi bazında bağımsızlık ilkesi doğrulanır.
     */
    @Test
    @DisplayName("Kriter 2: Öğretmen görüldü yapınca kendi sayısı 0 olur, yöneticininki 1 kalmaya devam eder")
    void testTeacherMarkSeenDoesNotAffectManager() throws Exception {
        createCompletedAssignment(studentUser, managerUser, "STAI");

        // Öğretmen görüldü işaretler
        MarkPsychResultsSeenRequest markReq = new MarkPsychResultsSeenRequest(studentUser.getId());
        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(teacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Öğretmenin sayısı 0'a düşmelidir
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnseen", is(0)))
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(0)));

        // Yöneticinin sayısı hala 1 kalmalıdır!
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));
    }

    /**
     * Kriter 3: İkinci "görüldü" çağrısı damgayı değiştirmez (ON CONFLICT DO NOTHING).
     */
    @Test
    @DisplayName("Kriter 3: İkinci görüldü çağrısı ilk görülme zaman damgasını korur (ON CONFLICT DO NOTHING)")
    void testSecondMarkSeenPreservesFirstTimestamp() throws Exception {
        PsychTestAssignment assignment = createCompletedAssignment(studentUser, managerUser, "STAI");

        MarkPsychResultsSeenRequest markReq = new MarkPsychResultsSeenRequest(studentUser.getId());

        // İlk çağrı
        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(teacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markReq)))
                .andExpect(status().isOk());

        PsychResultViewId viewId = new PsychResultViewId(assignment.getId(), teacherUser.getId());
        Optional<PsychResultView> firstView = resultViewRepository.findById(viewId);
        assertTrue(firstView.isPresent());
        Instant firstSeenAt = firstView.get().getSeenAt();

        // Kısa bekleme
        Thread.sleep(50);

        // İkinci çağrı
        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(teacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markReq)))
                .andExpect(status().isOk());

        Optional<PsychResultView> secondView = resultViewRepository.findById(viewId);
        assertTrue(secondView.isPresent());
        assertEquals(firstSeenAt, secondView.get().getSeenAt(), "İlk görülme zaman damgası korunmalıdır.");
    }

    /**
     * Kriter 4: Eşleşmemiş öğretmenin "görüldü" çağrısı 403, öğrencinin sayım ve "görüldü" çağrıları 403.
     */
    @Test
    @DisplayName("Kriter 4: Yetki kontrolleri - Eşleşmemiş öğretmen görüldü çağrısında 403, öğrenci her iki uçta 403 alır")
    void testAuthorizationAndIdorProtection() throws Exception {
        createCompletedAssignment(studentUser, managerUser, "STAI");

        MarkPsychResultsSeenRequest markReq = new MarkPsychResultsSeenRequest(studentUser.getId());

        // 1. Eşleşmemiş öğretmenin görüldü çağrısı -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(unpairedTeacherUser.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markReq)))
                .andExpect(status().isForbidden());

        // 2. Öğrencinin sayım çağrısı -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // 3. Öğrencinin görüldü çağrısı -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(studentUser.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markReq)))
                .andExpect(status().isForbidden());
    }

    /**
     * Kriter 5: Sonuç listeleme uçlarını çağırmak sayıyı değiştirmez (ENG-07 §1.1 - GET yan etki üretmez).
     */
    @Test
    @DisplayName("Kriter 5: Sonuç listeleme uçları (GET) yan etki üretmez, sayıyı değiştirmez")
    void testResultListingEndpointsProduceNoSideEffects() throws Exception {
        createCompletedAssignment(studentUser, managerUser, "STAI");

        // Öğretmen öğrencinin sonuçlarını listeler
        mockMvc.perform(get("/api/v1/psych-tests/results/student/" + studentUser.getId())
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Sayım çağrıldığında hala 1 olmalıdır (GET uçları görüldü kaydı eklemez)
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(teacherUser.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnseen", is(1)));

        // Yönetici tüm sonuçları listeler
        mockMvc.perform(get("/api/v1/psych-tests/results")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));

        // Yöneticinin sayımı da hala 1 olmalıdır
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));
    }

    /**
     * T-062B Kriteri: Yönetici 'görüldü' çağrısı yalnızca ekranda listelenen (isteğe eklenen)
     * tamamlanmış sonuçları işaretler. İsteğe eklenmeyen sonucun sayımı 1 kalır;
     * listedeki var olmayan veya henüz tamamlanmamış (PENDING) kimlikler yok sayılır (filtrelenir).
     */
    @Test
    @DisplayName("T-062B: Yönetici yalnızca belirtilen tamamlanmış sonuçları işaretler; isteğe eklenmeyen 1 kalır; geçersiz kimlikler yok sayılır")
    void testManagerMarksOnlySpecifiedAssignmentIds_UnspecifiedRemainsUnseen() throws Exception {
        // İki farklı tamamlanmış test oluştur: Test A ve Test B
        PsychTestAssignment assignmentA = createCompletedAssignment(studentUser, managerUser, "STAI");
        PsychTestAssignment assignmentB = createCompletedAssignment(studentUser, managerUser, "BOURDON");

        // Henüz tamamlanmamış (PENDING) bir test ve rastgele var olmayan bir UUID
        PsychTestAssignment pendingAssignment = new PsychTestAssignment(
                UUID.randomUUID(),
                "STAI",
                studentUser.getId(),
                managerUser.getId(),
                Instant.now(),
                PsychTestStatus.PENDING,
                null,
                null,
                null
        );
        PsychTestAssignment savedPending = assignmentRepository.save(pendingAssignment);
        createdAssignmentIds.add(savedPending.getId());
        UUID nonExistentId = UUID.randomUUID();

        // 1. Başlangıçta yönetici için 2 görülmemiş tamamlanmış test olmalıdır
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(2)));

        // 2. Yönetici görüldü çağrısında: Yalnızca assignmentA, pendingAssignment ve nonExistentId kimliklerini gönderir
        // (assignmentB İSTEYE EKLENMEZ!)
        MarkPsychResultsSeenRequest request = new MarkPsychResultsSeenRequest(
                null,
                List.of(assignmentA.getId(), savedPending.getId(), nonExistentId)
        );

        mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                        .with(csrf())
                        .with(user(managerUser.getEmail()).roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // 3. Kontrol:
        // assignmentA görüldü oldu.
        // assignmentB görüldü OLMADI (hala görülmemiş!).
        // pendingAssignment ve nonExistentId yok sayıldı (hata vermedi, DB'ye eklenmedi).
        // Yönetici için sayım 1 kalmalıdır!
        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));

        // AssignmentA'nın görüldü tablosunda var olduğunu doğrula
        assertTrue(resultViewRepository.findById(new PsychResultViewId(assignmentA.getId(), managerUser.getId())).isPresent());
        // AssignmentB'nin görüldü tablosunda olmadığını doğrula
        assertFalse(resultViewRepository.findById(new PsychResultViewId(assignmentB.getId(), managerUser.getId())).isPresent());
        // Pending ve nonExistent kayıtlarının eklenmediğini doğrula
        assertFalse(resultViewRepository.findById(new PsychResultViewId(savedPending.getId(), managerUser.getId())).isPresent());
        assertFalse(resultViewRepository.findById(new PsychResultViewId(nonExistentId, managerUser.getId())).isPresent());
    }

    /**
     * T-062C: Yöneticinin "görüldü" çağrısında atama kimlikleri zorunludur. Kimliksiz (boş gövde),
     * yalnızca studentId veya boş liste ile yapılan çağrı 400 alır ve hiçbir sonuç görüldü sayılmaz.
     * Kural sunucuda korunur (APP-01 §2.1): istemci ne gönderirse göndersin toptan işaretleme yolu yoktur.
     */
    @Test
    @DisplayName("T-062C: Yönetici atama kimliği göndermezse (boş gövde, yalnızca studentId, boş liste) 400 alır ve sayım değişmez")
    void testManagerMarkSeen_WithoutAssignmentIds_Returns400AndMarksNothing() throws Exception {
        PsychTestAssignment assignment = createCompletedAssignment(studentUser, managerUser, "STAI");

        List<String> bodies = List.of(
                "{}",
                objectMapper.writeValueAsString(new MarkPsychResultsSeenRequest(studentUser.getId())),
                objectMapper.writeValueAsString(new MarkPsychResultsSeenRequest(null, List.of()))
        );
        for (String body : bodies) {
            mockMvc.perform(post("/api/v1/psych-tests/results/mark-seen")
                            .with(csrf())
                            .with(user(managerUser.getEmail()).roles("MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/api/v1/psych-tests/unseen-counts")
                        .with(user(managerUser.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCounts['" + studentUser.getId() + "']", is(1)));
        assertFalse(resultViewRepository.findById(new PsychResultViewId(assignment.getId(), managerUser.getId())).isPresent());
    }
}

