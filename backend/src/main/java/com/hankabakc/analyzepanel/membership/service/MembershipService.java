package com.hankabakc.analyzepanel.membership.service;

import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.PasswordGenerator;
import com.hankabakc.analyzepanel.membership.dto.CreateUserRequest;
import com.hankabakc.analyzepanel.membership.dto.CreatedUserResponse;
import com.hankabakc.analyzepanel.membership.dto.PairingDto;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.membership.dto.ResetPasswordResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MembershipService: Üyelik süreçlerini, kullanıcı oluşturma/onay süreçlerini ve 
 * hiyerarşik (öğretmen-öğrenci) eşleşmeleri yöneten servis katmanıdır.
 */
@Service
public class MembershipService {

    private final AppUserRepository userRepository;
    private final EntityManager entityManager;
    private final GeneratedEmailFactory generatedEmailFactory;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /**
     * Constructor Injection: Gerekli bağımlılıklar enjekte edilir (Pure Java).
     */
    public MembershipService(
            AppUserRepository userRepository, 
            EntityManager entityManager,
            GeneratedEmailFactory generatedEmailFactory,
            PasswordGenerator passwordGenerator,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.generatedEmailFactory = generatedEmailFactory;
        this.passwordGenerator = passwordGenerator;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * createUser: Yöneticinin yeni bir öğretmen veya öğrenci hesabı oluşturmasını sağlar (T-010).
     * 
     * <p>İş Kuralları (T-010 / K1-K6):</p>
     * <ul>
     *   <li>K1: Telefon numarası isteğe bağlıdır; boş veya boşluklu ise null kaydedilir.</li>
     *   <li>K2: Benzersizlik denetimi yalnızca telefon numarası doluysa yapılır.</li>
     *   <li>K3: Sunucu PasswordGenerator ile 12 karakterlik güvenli ilk şifre üretir ve yalnızca bu yanıtta döner.</li>
     *   <li>K4: Şifre loga yazılmaz; veritabanında yalnızca BCrypt cost 12 hash olarak saklanır.</li>
     *   <li>K5: Yeni kullanıcı must_change_password = true olarak açılır.</li>
     *   <li>K6: E-posta ad soyaddan otomatik üretilir.</li>
     * </ul>
     * 
     * @param request Yeni kullanıcı istek parametreleri
     * @return Oluşturulan kullanıcının CreatedUserResponse nesnesi
     */
    @Transactional
    public CreatedUserResponse createUser(CreateUserRequest request) {
        // Yönetici rolü yaratılamaz (Güvenlik / Yetki yükseltme engeli)
        if (request.role() == UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yönetici rolünde kullanıcı oluşturulamaz.");
        }

        // Sınıf seviyesi denetimi (T-016: Yalnızca öğrenci için zorunlu ve 5-12 arası olmalıdır)
        Integer grade = null;
        if (request.role() == UserRole.STUDENT) {
            if (request.grade() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci için sınıf seviyesi seçilmelidir.");
            }
            if (request.grade() < 5 || request.grade() > 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sınıf seviyesi 5 ile 12 arasında olmalıdır.");
            }
            grade = request.grade();
        }

        // K6: Otomatik iç kimlik e-postası üretimi
        String generatedEmail = generatedEmailFactory.generateUniqueEmail(request.fullName());

        // K3, K4, K5: Şifre üretimi, BCrypt hashleme ve mustChangePassword = true ataması
        String rawPassword = passwordGenerator.generateReadablePassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        AppUser user = new AppUser(
            generatedEmail,
            request.fullName().trim(),
            request.role(),
            UserStatus.ACTIVE,
            grade,
            encodedPassword,
            true
        );

        user = userRepository.save(user);

        UserDto userDto = new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getGrade(),
            user.isMustChangePassword()
        );

        return new CreatedUserResponse(userDto, rawPassword);
    }

    /**
     * getActiveTeachers: Sistemdeki onaylanmış (ACTIVE) tüm eğitmenleri getirir.
     * Eşleştirme merkezinde seçim yapmak için kullanılır.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getActiveTeachers() {
        return userRepository.findAllByRoleAndStatus(UserRole.TEACHER, UserStatus.ACTIVE).stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(), u.getGrade(), u.isMustChangePassword()))
                .toList();
    }

    /**
     * getActiveStudents: Sistemdeki onaylanmış (ACTIVE) tüm öğrencileri getirir.
     * Genel bakışta ve kullanıcı yönetiminde kullanılır.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getActiveStudents() {
        return userRepository.findAllByRoleAndStatus(UserRole.STUDENT, UserStatus.ACTIVE).stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(), u.getGrade(), u.isMustChangePassword()))
                .toList();
    }

    /**
     * getUnpairedStudents: Sistemde kayıtlı olan ve henüz hiçbir eğitmenle eşleştirilmemiş aktif öğrencileri getirir (T-021).
     * Eşleştirme merkezinde yalnızca eşleşmeye uygun öğrencileri listelemek için kullanılır.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<UserDto> getUnpairedStudents() {
        List<AppUser> users = entityManager.createNativeQuery(
                "SELECT u.* FROM app_users u " +
                "WHERE u.role = 'STUDENT' AND u.status = 'ACTIVE' " +
                "AND u.id NOT IN (SELECT student_id FROM student_teacher_pairings)", AppUser.class)
                .getResultList();

        return users.stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(), u.getGrade(), u.isMustChangePassword()))
                .toList();
    }

    /**
     * getStudentsOfTeacher: Belirli bir öğretmene atanmış olan tüm öğrencileri listeler.
     * Native Query ile hiyerarşi tablosu (pairings) ve kullanıcı tablosu join edilir.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<UserDto> getStudentsOfTeacher(UUID teacherId) {
        List<AppUser> users = entityManager.createNativeQuery(
                "SELECT u.* FROM app_users u " +
                "JOIN student_teacher_pairings p ON u.id = p.student_id " +
                "WHERE p.teacher_id = :tId", AppUser.class)
                .setParameter("tId", teacherId)
                .getResultList();
        
        return users.stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(), u.getGrade(), u.isMustChangePassword()))
                .toList();
    }

    /**
     * getTeachersOfStudent: Belirli bir öğrenciye atanmış olan tüm öğretmenleri listeler.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<UserDto> getTeachersOfStudent(UUID studentId) {
        List<AppUser> users = entityManager.createNativeQuery(
                "SELECT u.* FROM app_users u " +
                "JOIN student_teacher_pairings p ON u.id = p.teacher_id " +
                "WHERE p.student_id = :sId", AppUser.class)
                .setParameter("sId", studentId)
                .getResultList();

        return users.stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(), u.getGrade(), u.isMustChangePassword()))
                .toList();
    }

    /**
     * pairStudentWithTeacher: Bir öğrenciyi bir öğretmenle eşleştirir (T-021).
     * 
     * <p>İş ve Güvenlik Kuralları (APP-01 §2.1, ENG-03 §1.1):</p>
     * <ul>
     *   <li>1. Girdi kimliklerinin varlığı doğrulanır (404 NOT_FOUND).</li>
     *   <li>2. Kullanıcıların rolleri denetlenir (öğrenci STUDENT, eğitmen TEACHER olmalıdır; 400 BAD_REQUEST).</li>
     *   <li>3. Öğrencinin zaten bir eşleşmesi varsa işlem reddedilir (400 BAD_REQUEST, "Bu öğrenci zaten bir eğitmenle eşleştirilmiş.").</li>
     *   <li>4. ON CONFLICT DO NOTHING kaldırılarak doğrudan INSERT yapılır; veritabanındaki UNIQUE kısıtı ile de çift katmanlı korunur.</li>
     * </ul>
     */
    @Transactional
    public void pairStudentWithTeacher(PairingRequest request) {
        if (request == null || request.studentId() == null || request.teacherId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci ve eğitmen kimliği zorunludur.");
        }

        AppUser student = userRepository.findById(request.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Öğrenci bulunamadı."));
        
        AppUser teacher = userRepository.findById(request.teacherId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Öğretmen bulunamadı."));

        if (student.getRole() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eşleştirilecek kullanıcı bir öğrenci olmalıdır.");
        }
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eşleştirilecek hedef kullanıcı bir öğretmen olmalıdır.");
        }

        // T-021: Öğrenci zaten bir eğitmenle eşleşmiş mi kontrol edilir (öğrenci başına tek öğretmen)
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM student_teacher_pairings WHERE student_id = :sId")
                .setParameter("sId", student.getId())
                .getSingleResult();

        if (count.longValue() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu öğrenci zaten bir eğitmenle eşleştirilmiş.");
        }

        entityManager.createNativeQuery(
                "INSERT INTO student_teacher_pairings (student_id, teacher_id) VALUES (:sId, :tId)")
                .setParameter("sId", student.getId())
                .setParameter("tId", teacher.getId())
                .executeUpdate();
    }

    /**
     * getAllPairings: Sistemdeki tüm aktif öğretmen-öğrenci eşleşmelerini listeler (T-036).
     * 
     * <p>Güvenlik ve Mimari İlkeler (ENG-11 §3.1, Pure Java):</p>
     * <ul>
     *   <li>1. Yalnızca yöneticiye açıktır.</li>
     *   <li>2. Öğretmen ve öğrenci bilgileri PiiConverter üzerinden güvenle çözülerek PairingDto listesine dönüştürülür.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<PairingDto> getAllPairings() {
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT p.student_id, p.teacher_id, p.paired_at FROM student_teacher_pairings p ORDER BY p.paired_at DESC")
                .getResultList();

        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = new HashSet<>();
        for (Object[] row : rows) {
            userIds.add((UUID) row[0]);
            userIds.add((UUID) row[1]);
        }

        Map<UUID, AppUser> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        List<PairingDto> pairings = new ArrayList<>();
        for (Object[] row : rows) {
            UUID studentId = (UUID) row[0];
            UUID teacherId = (UUID) row[1];
            Instant pairedAt = null;
            if (row[2] instanceof java.sql.Timestamp ts) {
                pairedAt = ts.toInstant();
            } else if (row[2] instanceof Instant inst) {
                pairedAt = inst;
            } else if (row[2] instanceof java.time.OffsetDateTime odt) {
                pairedAt = odt.toInstant();
            }

            AppUser student = userMap.get(studentId);
            AppUser teacher = userMap.get(teacherId);

            if (student != null && teacher != null) {
                pairings.add(new PairingDto(
                        student.getId(),
                        student.getFullName(),
                        student.getEmail(),
                        student.getGrade(),
                        teacher.getId(),
                        teacher.getFullName(),
                        teacher.getEmail(),
                        pairedAt
                ));
            }
        }
        return pairings;
    }

    /**
     * unpairStudentWithTeacher: Bir öğrenci ile öğretmen arasındaki eşleşmeyi sonlandırır (T-036).
     * 
     * <p>Güvenlik ve Sıfır Veri Kaybı Kuralları (APP-03 §3, ENG-11 §3.1, ENG-03 §1.1):</p>
     * <ul>
     *   <li>1. Girdi kimlikleri doğrulanır (400 BAD_REQUEST).</li>
     *   <li>2. Eşleşme kaydı yoksa 404 NOT_FOUND döner ("Eşleşme kaydı bulunamadı.").</li>
     *   <li>3. Yalnızca student_teacher_pairings tablosundaki satır silinir.</li>
     *   <li>4. analysis_reports, exam_summaries ve öğrenci kullanıcı verilerine KESİNLİKLE dokunulmaz (Sıfır Veri Kaybı).</li>
     * </ul>
     */
    @Transactional
    public void unpairStudentWithTeacher(PairingRequest request) {
        if (request == null || request.studentId() == null || request.teacherId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci ve eğitmen kimliği zorunludur.");
        }

        int deletedRows = entityManager.createNativeQuery(
                "DELETE FROM student_teacher_pairings WHERE student_id = :sId AND teacher_id = :tId")
                .setParameter("sId", request.studentId())
                .setParameter("tId", request.teacherId())
                .executeUpdate();

        if (deletedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Eşleşme kaydı bulunamadı.");
        }
    }

    /**
     * isTeacherOfStudent: Bir öğretmenin belirli bir öğrenciye erişim yetkisi olup olmadığını kontrol eder.
     * Güvenlik (IDOR) kontrolleri için kullanılır.
     */
    @Transactional(readOnly = true)
    public boolean isTeacherOfStudent(UUID teacherId, UUID studentId) {
        Long count = (Long) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM student_teacher_pairings " +
                "WHERE teacher_id = :tId AND student_id = :sId")
                .setParameter("tId", teacherId)
                .setParameter("sId", studentId)
                .getSingleResult();
        return count > 0;
    }

    /**
     * resetUserPassword: Yöneticinin bir öğretmen veya öğrenci için yeni geçici şifre üretmesini sağlar (T-013).
     * 
     * <p>Güvenlik ve Mühendislik Kuralları (ENG-11 §1.1, §2.4, §3.1 / APP-01 §2.1, §2.2):</p>
     * <ul>
     *   <li>1. Hedef kullanıcı yoksa 404 NOT_FOUND döner.</li>
     *   <li>2. Hedef kullanıcı MANAGER rolündeyse 400 BAD_REQUEST ile işlem reddedilir (Yetki devralma engeli).</li>
     *   <li>3. PasswordGenerator ile en az 12 karakterlik güvenli şifre üretilir ve BCrypt cost 12 ile hash'lenir.</li>
     *   <li>4. mustChangePassword = true olarak ayarlanır (kullanıcı ilk girişte şifre değiştirmek zorundadır).</li>
     *   <li>5. ENG-11 §2.4: Kullanıcının tüm açık oturumları (refresh tokens) derhal sonlandırılır.</li>
     *   <li>6. Şifre loga yazılmaz; yalnızca bu metodun tek seferlik yanıtında (ResetPasswordResponse) döner.</li>
     * </ul>
     * 
     * @param userId Şifresi sıfırlanacak kullanıcının benzersiz kimliği
     * @return Yeni şifre ve kullanıcı DTO'sunu içeren ResetPasswordResponse
     */
    @Transactional
    public ResetPasswordResponse resetUserPassword(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));

        // Yönetici şifresi bu panelden sıfırlanamaz (Güvenlik / Yetki devralma engeli)
        if (user.getRole() == UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yönetici hesabı için şifre sıfırlanamaz.");
        }

        // K3, K4: Güvenli şifre üretimi ve BCrypt hashleme
        String rawPassword = passwordGenerator.generateReadablePassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        user.setCredentialsInvalidatedAt(java.time.Instant.now());
        userRepository.save(user);

        // ENG-11 §2.4: Tüm aktif ve eski refresh token oturumlarını düşür
        refreshTokenService.deleteByUserId(user);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getGrade(),
                user.isMustChangePassword()
        );

        return new ResetPasswordResponse(userDto, rawPassword);
    }

    /**
     * getActiveManagerCount: Sistemdeki aktif yönetici (MANAGER) sayısını döndürür (T-075).
     *
     * @return Aktif yönetici sayısı
     */
    @Transactional(readOnly = true)
    public long getActiveManagerCount() {
        return userRepository.countByRoleAndStatus(UserRole.MANAGER, UserStatus.ACTIVE);
    }
}
