package com.hankabakc.analyzepanel.membership;

import com.hankabakc.analyzepanel.auth.dto.LoginRequest;
import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.AuthService;
import com.hankabakc.analyzepanel.membership.dto.CreateUserRequest;
import com.hankabakc.analyzepanel.membership.dto.CreatedUserResponse;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MembershipIntegrationTest: T-004 ve T-010 kapsamında yöneticinin kullanıcı oluşturma
 * süreçlerini, e-posta üretimini, otomatik şifre üretimini
 * ve şifreli oturum açma entegrasyonunu test eder.
 */
@SpringBootTest
public class MembershipIntegrationTest {

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.hankabakc.analyzepanel.auth.service.RefreshTokenService refreshTokenService;

    @Autowired
    private com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository;

    private java.time.LocalDateTime testStartTime;

    private final java.util.List<java.util.UUID> trackedUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();


    @BeforeEach
    void setUp() {
        testStartTime = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private CreatedUserResponse createUser(CreateUserRequest request) {
        CreatedUserResponse res = membershipService.createUser(request);
        if (res != null && res.user() != null && res.user().id() != null) {
            trackedUserIds.add(res.user().id());
        }
        return res;
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
    @DisplayName("T-004 / T-010 / T-016: Öğretmen oluşturulur -> ACTIVE doğar, sınıfı null kalır, şifre üretilir, e-posta otomatik üretilir")
    void testCreateTeacher_Success() {
        CreateUserRequest request = new CreateUserRequest(
                "Mehmet Demir",
                UserRole.TEACHER,
                12 // Öğretmene gönderilse bile yutulmalı (K3 / T-016)
        );

        CreatedUserResponse res = createUser(request);
        UserDto created = res.user();

        assertNotNull(created);
        assertNotNull(res.generatedPassword());
        assertTrue(res.generatedPassword().length() >= 12);
        assertTrue(created.mustChangePassword());
        assertEquals(UserRole.TEACHER, created.role());
        assertEquals(UserStatus.ACTIVE, created.status());
        assertNull(created.grade(), "Öğretmen oluşturulurken sınıf bilgisi null olmalıdır");
        assertEquals("mehmet.demir@analyzepanel.local", created.email());

        // Veritabanından doğrudan doğrula
        Optional<AppUser> userOpt = userRepository.findById(created.id());
        assertTrue(userOpt.isPresent());
        assertEquals(UserStatus.ACTIVE, userOpt.get().getStatus());
        assertNull(userOpt.get().getGrade());
        assertNotNull(userOpt.get().getPassword());
        assertTrue(userOpt.get().isMustChangePassword());
    }

    @Test
    @DisplayName("T-004: Aynı ad soyadla ikinci kullanıcı -> Çakışmasız ...2@analyzepanel.local e-postası üretilir")
    void testCreateUser_EmailCollisionHandling() {
        CreateUserRequest req1 = new CreateUserRequest(
                "Zeynep Kaya",
                UserRole.STUDENT,
                12
        );
        CreatedUserResponse res1 = createUser(req1);
        assertEquals("zeynep.kaya@analyzepanel.local", res1.user().email());

        CreateUserRequest req2 = new CreateUserRequest(
                "Zeynep Kaya",
                UserRole.STUDENT,
                11
        );
        CreatedUserResponse res2 = createUser(req2);
        assertEquals("zeynep.kaya2@analyzepanel.local", res2.user().email());
        assertEquals(11, res2.user().grade());
    }

    @Test
    @DisplayName("T-004: Türkçe karakterli isimler ASCII'ye doğru dönüştürülür")
    void testCreateUser_TurkishTransliteration() {
        CreateUserRequest request = new CreateUserRequest(
                "Şükrü Çiğdem İbrahim",
                UserRole.TEACHER,
                null
        );

        CreatedUserResponse res = createUser(request);
        assertEquals("sukru.cigdem.ibrahim@analyzepanel.local", res.user().email());
    }

    @Test
    @DisplayName("T-004 Güvenlik: MANAGER rolü ile kullanıcı oluşturma 400 ile reddedilir")
    void testCreateManager_Rejected() {
        CreateUserRequest request = new CreateUserRequest(
                "Hacker Manager",
                UserRole.MANAGER,
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                createUser(request)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Yönetici rolünde kullanıcı oluşturulamaz"));
    }

    @Test
    @DisplayName("T-016: Öğrenci için sınıf seviyesi girilmezse 400 ile reddedilir")
    void testStudentWithoutGrade_Rejected() {
        CreateUserRequest request = new CreateUserRequest(
                "Sınıfsız Öğrenci",
                UserRole.STUDENT,
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                createUser(request)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("sınıf seviyesi seçilmelidir"));
    }

    @Test
    @DisplayName("T-016: Öğrenci için 5-12 dışındaki sınıf seviyeleri (4 ve 13) 400 ile reddedilir")
    void testStudentWithInvalidGrade_Rejected() {
        CreateUserRequest reqLow = new CreateUserRequest(
                "Düşük Sınıf Öğrenci",
                UserRole.STUDENT,
                4
        );
        ResponseStatusException exLow = assertThrows(ResponseStatusException.class, () ->
                createUser(reqLow)
        );
        assertEquals(400, exLow.getStatusCode().value());
        assertTrue(exLow.getReason().contains("5 ile 12 arasında olmalıdır"));

        CreateUserRequest reqHigh = new CreateUserRequest(
                "Yüksek Sınıf Öğrenci",
                UserRole.STUDENT,
                13
        );
        ResponseStatusException exHigh = assertThrows(ResponseStatusException.class, () ->
                createUser(reqHigh)
        );
        assertEquals(400, exHigh.getStatusCode().value());
        assertTrue(exHigh.getReason().contains("5 ile 12 arasında olmalıdır"));
    }

    @Test
    @DisplayName("B-11 İş Kuralı: Öğretmen için grade gönderilse bile yok sayılır ve null kaydedilir")
    void testCreateTeacher_GradeExplicitlyIgnored() {
        CreateUserRequest request = new CreateUserRequest(
                "Mehmet Demir",
                UserRole.TEACHER,
                12 // İstemci veya saldırgan sınıf alanı gönderdi
        );

        CreatedUserResponse res = createUser(request);
        UserDto created = res.user();

        assertNotNull(created);
        assertEquals(UserRole.TEACHER, created.role());
        assertNull(created.grade(), "Dönen UserDto içinde grade null olmalıdır");

        // Doğrudan veritabanı kaydını doğrula
        Optional<AppUser> userOpt = userRepository.findById(created.id());
        assertTrue(userOpt.isPresent());
        assertNull(userOpt.get().getGrade(), "Veritabanındaki AppUser kaydında grade null olmalıdır");
    }

    // ==========================================
    // T-010 YENİ TESTLERİ
    // ==========================================

    @Test
    @DisplayName("T-010 / K1: Kullanıcı oluşturulunca e-posta ve şifre üretilir, must_change_password true")
    void testCreateUser_GeneratesEmailAndPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "Telefonsuz Öğrenci",
                UserRole.STUDENT,
                10
        );

        CreatedUserResponse res = createUser(request);

        assertNotNull(res);
        assertNotNull(res.user().id());
        assertNotNull(res.generatedPassword());
        assertTrue(res.generatedPassword().length() >= 12);
        assertEquals("telefonsuz.ogrenci@analyzepanel.local", res.user().email());
        assertTrue(res.user().mustChangePassword());
        assertEquals(10, res.user().grade());

        // Veritabanı doğrulaması
        AppUser user = userRepository.findById(res.user().id()).orElseThrow();
        assertNotNull(user.getPassword(), "Şifre hash'lenmiş olmalıdır");
        assertTrue(user.isMustChangePassword());
        assertEquals(10, user.getGrade());
    }

    @Test
    @DisplayName("T-010 / K2: Aynı adlı iki kullanıcı arka arkaya eklenir -> ikisi de ayrı kayıt olarak oluşturulur")
    void testCreateMultipleUsers_SameName_Success() {
        CreateUserRequest req1 = new CreateUserRequest(
                "Ali Can",
                UserRole.STUDENT,
                11
        );
        CreatedUserResponse res1 = createUser(req1);
        assertNotNull(res1.user().id());

        CreateUserRequest req2 = new CreateUserRequest(
                "Ali Can",
                UserRole.STUDENT,
                11
        );
        CreatedUserResponse res2 = createUser(req2);
        assertNotNull(res2.user().id());

        AppUser u1 = userRepository.findById(res1.user().id()).orElseThrow();
        AppUser u2 = userRepository.findById(res2.user().id()).orElseThrow();
        assertNotEquals(u1.getId(), u2.getId());
    }

    @Test
    @DisplayName("T-010 / K3 & K5: Üretilen şifre ile /auth/login çağrılır -> Giriş başarılı olur ve mustChangePassword true döner")
    void testCreatedUser_LoginWithGeneratedPassword_Success() {
        CreateUserRequest request = new CreateUserRequest(
                "Telefonsuz Öğrenci",
                UserRole.STUDENT,
                12
        );
        CreatedUserResponse res = createUser(request);
        String plainPassword = res.generatedPassword();
        String email = res.user().email();

        // Üretilen şifre ile oturum açmayı dene
        LoginRequest loginRequest = new LoginRequest(email, plainPassword, false);
        java.util.Map<String, Object> loginResult = authService.login(loginRequest);

        assertNotNull(loginResult);
        UserDto loginDto = (UserDto) loginResult.get("user");
        assertNotNull(loginDto);
        assertEquals(email, loginDto.email());
        assertTrue(loginDto.mustChangePassword(), "Yeni oluşturulan kullanıcı mustChangePassword = true ile giriş yapmalıdır");
    }

    @Test
    @DisplayName("T-013: resetUserPassword çağrıldığında yeni şifre atanır, mustChangePassword true olur, eski şifre geçersiz kalır ve oturumlar silinir")
    void testResetUserPassword_Success_UpdatesPassword_RevokesSessions() {
        // 1. Öğrenci oluştur
        CreateUserRequest request = new CreateUserRequest(
                "Sıfırlama Test Öğrenci",
                UserRole.STUDENT,
                11
        );
        CreatedUserResponse createdRes = createUser(request);
        String oldPassword = createdRes.generatedPassword();
        String email = createdRes.user().email();
        java.util.UUID userId = createdRes.user().id();

        // 2. İlk şifreyle giriş yap ve oturum aç
        LoginRequest initialLogin = new LoginRequest(email, oldPassword, false);
        java.util.Map<String, Object> initialResult = authService.login(initialLogin);
        String initialRefreshToken = (String) initialResult.get("refreshToken");
        assertNotNull(initialRefreshToken);

        // 3. Yönetici şifre sıfırlama servisini çağırır
        com.hankabakc.analyzepanel.membership.dto.ResetPasswordResponse resetRes = membershipService.resetUserPassword(userId);
        assertNotNull(resetRes);
        assertNotNull(resetRes.generatedPassword());
        assertTrue(resetRes.generatedPassword().length() >= 12);
        assertTrue(resetRes.user().mustChangePassword());
        assertNotEquals(oldPassword, resetRes.generatedPassword());

        // 4. Eski şifreyle giriş denenir -> 401 Unauthorized
        LoginRequest oldLoginReq = new LoginRequest(email, oldPassword, false);
        assertThrows(ResponseStatusException.class, () -> authService.login(oldLoginReq));

        // 5. Yeni şifreyle giriş başarılı olur ve mustChangePassword = true döner
        LoginRequest newLoginReq = new LoginRequest(email, resetRes.generatedPassword(), false);
        java.util.Map<String, Object> newResult = authService.login(newLoginReq);
        assertNotNull(newResult);
        UserDto newUserDto = (UserDto) newResult.get("user");
        assertTrue(newUserDto.mustChangePassword());
    }

    @Test
    @DisplayName("T-013: resetUserPassword hedef kullanıcı MANAGER olduğunda 400 Bad Request fırlatır")
    void testResetUserPassword_TargetIsManager_ThrowsException() {
        // Yönetici kullanıcısını bul
        AppUser manager = userRepository.findByEmail("admin@admin.com")
                .orElseGet(() -> {
                    AppUser m = new AppUser("admin.test@admin.com", "Admin Test", UserRole.MANAGER, UserStatus.ACTIVE, null, "$2a$12$...", false);
                    AppUser saved = userRepository.save(m);
                    trackedUserIds.add(saved.getId());
                    return saved;
                });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> membershipService.resetUserPassword(manager.getId()));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Yönetici hesabı için şifre sıfırlanamaz.", ex.getReason());
    }

    @Test
    @DisplayName("T-013B / B-46: resetUserPassword sonrası kullanıcının credentialsInvalidatedAt alanı güncellenir")
    void testResetUserPassword_UpdatesCredentialsInvalidatedAt() {
        CreateUserRequest request = new CreateUserRequest(
                "Sıfırlama Zaman Damgası Öğrenci",
                UserRole.STUDENT,
                11
        );
        CreatedUserResponse createdRes = createUser(request);
        java.util.UUID userId = createdRes.user().id();

        AppUser beforeReset = userRepository.findById(userId).orElseThrow();
        assertNull(beforeReset.getCredentialsInvalidatedAt());

        membershipService.resetUserPassword(userId);

        AppUser afterReset = userRepository.findById(userId).orElseThrow();
        assertNotNull(afterReset.getCredentialsInvalidatedAt());
        assertTrue(afterReset.getCredentialsInvalidatedAt().isAfter(java.time.Instant.now().minusSeconds(10)));
    }

    @Test
    @DisplayName("T-021: Bir öğrenci öğretmenle başarıyla eşleştirilir, eşleşmemiş öğrenci listesinden düşer")
    void testPairStudentWithTeacher_Success() {
        CreateUserRequest teacherReq = new CreateUserRequest("Eşleşme Öğretmen 1", UserRole.TEACHER, null);
        CreateUserRequest studentReq = new CreateUserRequest("Eşleşme Öğrenci 1", UserRole.STUDENT, 8);

        CreatedUserResponse teacherRes = createUser(teacherReq);
        CreatedUserResponse studentRes = createUser(studentReq);

        java.util.UUID teacherId = teacherRes.user().id();
        java.util.UUID studentId = studentRes.user().id();

        // 1. Eşleştirme öncesinde öğrenci eşleşmemişler listesinde bulunur
        java.util.List<UserDto> unpairedBefore = membershipService.getUnpairedStudents();
        assertTrue(unpairedBefore.stream().anyMatch(u -> u.id().equals(studentId)));

        // 2. Eşleştirme yapılır -> Başarılı
        com.hankabakc.analyzepanel.membership.dto.PairingRequest pairingReq =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentId, teacherId);
        assertDoesNotThrow(() -> membershipService.pairStudentWithTeacher(pairingReq));

        // 3. Eşleşme durumu doğrulanır
        assertTrue(membershipService.isTeacherOfStudent(teacherId, studentId));
        java.util.List<UserDto> teacherStudents = membershipService.getStudentsOfTeacher(teacherId);
        assertTrue(teacherStudents.stream().anyMatch(u -> u.id().equals(studentId)));

        // 4. Eşleştirme sonrasında öğrenci eşleşmemişler listesinden düşer (T-021)
        java.util.List<UserDto> unpairedAfter = membershipService.getUnpairedStudents();
        assertFalse(unpairedAfter.stream().anyMatch(u -> u.id().equals(studentId)));
    }

    @Test
    @DisplayName("T-021: Aynı öğrenci aynı öğretmenle tekrar eşleştirilmek istendiğinde 400 Bad Request fırlatılır")
    void testPairStudentWithTeacher_AlreadyPairedSameTeacher_ThrowsBadRequest() {
        CreateUserRequest teacherReq = new CreateUserRequest("Eşleşme Öğretmen 2", UserRole.TEACHER, null);
        CreateUserRequest studentReq = new CreateUserRequest("Eşleşme Öğrenci 2", UserRole.STUDENT, 7);

        CreatedUserResponse teacherRes = createUser(teacherReq);
        CreatedUserResponse studentRes = createUser(studentReq);

        java.util.UUID teacherId = teacherRes.user().id();
        java.util.UUID studentId = studentRes.user().id();

        com.hankabakc.analyzepanel.membership.dto.PairingRequest pairingReq =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentId, teacherId);

        // İlk eşleştirme başarılı
        membershipService.pairStudentWithTeacher(pairingReq);

        // İkinci eşleştirme denemesi 400 fırlatmalı
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> membershipService.pairStudentWithTeacher(pairingReq));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bu öğrenci zaten bir eğitmenle eşleştirilmiş.", ex.getReason());
    }

    @Test
    @DisplayName("T-021: Zaten eşleşmiş bir öğrenci farklı bir öğretmenle eşleştirilmek istendiğinde 400 Bad Request fırlatılır")
    void testPairStudentWithTeacher_AlreadyPairedDifferentTeacher_ThrowsBadRequest() {
        CreateUserRequest teacher1Req = new CreateUserRequest("Eşleşme Öğretmen A", UserRole.TEACHER, null);
        CreateUserRequest teacher2Req = new CreateUserRequest("Eşleşme Öğretmen B", UserRole.TEACHER, null);
        CreateUserRequest studentReq = new CreateUserRequest("Eşleşme Öğrenci Çoklu Deneme", UserRole.STUDENT, 9);

        CreatedUserResponse teacher1Res = createUser(teacher1Req);
        CreatedUserResponse teacher2Res = createUser(teacher2Req);
        CreatedUserResponse studentRes = createUser(studentReq);

        java.util.UUID teacher1Id = teacher1Res.user().id();
        java.util.UUID teacher2Id = teacher2Res.user().id();
        java.util.UUID studentId = studentRes.user().id();

        // 1. Öğretmen 1 ile eşleştirme -> Başarılı
        membershipService.pairStudentWithTeacher(new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentId, teacher1Id));

        // 2. Öğretmen 2 ile eşleştirme denemesi -> 400 Bad Request
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                membershipService.pairStudentWithTeacher(new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentId, teacher2Id)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Bu öğrenci zaten bir eğitmenle eşleştirilmiş.", ex.getReason());
    }

    @Test
    @DisplayName("T-036: Yönetici eşleşmeyi ayırdığında öğrenci boşa çıkar ve tekrar eşleşmemişler listesine döner")
    void testUnpairStudentWithTeacher_SucceedsAndReleasesStudentToUnpairedList() {
        CreateUserRequest teacherReq = new CreateUserRequest("Ayırma Öğretmen", UserRole.TEACHER, null);
        CreateUserRequest studentReq = new CreateUserRequest("Ayırma Öğrenci", UserRole.STUDENT, 11);

        CreatedUserResponse teacherRes = createUser(teacherReq);
        CreatedUserResponse studentRes = createUser(studentReq);

        java.util.UUID teacherId = teacherRes.user().id();
        java.util.UUID studentId = studentRes.user().id();

        com.hankabakc.analyzepanel.membership.dto.PairingRequest pairingReq =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentId, teacherId);

        // 1. Eşleştirilir
        membershipService.pairStudentWithTeacher(pairingReq);
        assertTrue(membershipService.isTeacherOfStudent(teacherId, studentId));
        assertFalse(membershipService.getUnpairedStudents().stream().anyMatch(u -> u.id().equals(studentId)));

        // 2. getAllPairings listesinde görünür
        java.util.List<com.hankabakc.analyzepanel.membership.dto.PairingDto> pairings = membershipService.getAllPairings();
        assertTrue(pairings.stream().anyMatch(p -> p.studentId().equals(studentId) && p.teacherId().equals(teacherId)));

        // 3. Eşleşme bozulur (unpair)
        assertDoesNotThrow(() -> membershipService.unpairStudentWithTeacher(pairingReq));

        // 4. Yetki düşer ve öğrenci eşleşmemişler listesine geri döner
        assertFalse(membershipService.isTeacherOfStudent(teacherId, studentId));
        assertTrue(membershipService.getUnpairedStudents().stream().anyMatch(u -> u.id().equals(studentId)));

        // 5. getAllPairings listesinden düşer
        java.util.List<com.hankabakc.analyzepanel.membership.dto.PairingDto> pairingsAfter = membershipService.getAllPairings();
        assertFalse(pairingsAfter.stream().anyMatch(p -> p.studentId().equals(studentId) && p.teacherId().equals(teacherId)));

        // 6. Tekrar eşleştirilebilir (Geri dönüş)
        assertDoesNotThrow(() -> membershipService.pairStudentWithTeacher(pairingReq));
        assertTrue(membershipService.isTeacherOfStudent(teacherId, studentId));
    }

    @Test
    @DisplayName("T-036: Mevcut olmayan bir eşleşme bozulmak istendiğinde 404 Not Found fırlatılır")
    void testUnpairStudentWithTeacher_NonExistentPairing_ThrowsNotFound() {
        CreateUserRequest teacherReq = new CreateUserRequest("Mevcutsuz Öğretmen", UserRole.TEACHER, null);
        CreateUserRequest studentReq = new CreateUserRequest("Mevcutsuz Öğrenci", UserRole.STUDENT, 8);

        CreatedUserResponse teacherRes = createUser(teacherReq);
        CreatedUserResponse studentRes = createUser(studentReq);

        com.hankabakc.analyzepanel.membership.dto.PairingRequest pairingReq =
                new com.hankabakc.analyzepanel.membership.dto.PairingRequest(studentRes.user().id(), teacherRes.user().id());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                membershipService.unpairStudentWithTeacher(pairingReq));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Eşleşme kaydı bulunamadı.", ex.getReason());
    }
}
