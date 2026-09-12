package com.hankabakc.analyzepanel.membership.service;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.ClassDto;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ClassService: Adlandırılmış sınıfları ve öğrenci üyeliklerini yönetir (T-040).
 *
 * <p><b>Kavram ayrımı:</b> {@code app_users.grade} "kaçıncı sınıf" (5-12, seviye) bilgisidir ve
 * T-016/S-010 kararıyla korunur. Buradaki sınıf ise <b>adlandırılmış gruptur</b> (örn. "12-A").
 * İkisi yan yana yaşar; bu servis {@code grade} alanına dokunmaz.</p>
 *
 * <p><b>Veri kaybı olmaz:</b> Öğrenciyi sınıftan çıkarmak yalnızca {@code class_id} alanını
 * boşaltır; karne, sınav, ders/konu ve eşleşme verisi bu alana bağlı değildir. Sınıf silindiğinde
 * de öğrenciler silinmez, sınıfsız kalır ({@code ON DELETE SET NULL}).</p>
 */
@Service
public class ClassService {

    private final EntityManager entityManager;
    private final AppUserRepository userRepository;

    public ClassService(EntityManager entityManager, AppUserRepository userRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    /** getAllClasses: Sınıfları ve içindeki öğrencileri döndürür. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ClassDto> getAllClasses() {
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, name, created_at FROM classes ORDER BY LOWER(name)")
                .getResultList();
        if (rows.isEmpty()) return List.of();

        Map<UUID, List<ClassDto.ClassStudentDto>> ogrenciler = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.STUDENT && u.getClassId() != null)
                .collect(Collectors.groupingBy(AppUser::getClassId,
                        Collectors.mapping(u -> new ClassDto.ClassStudentDto(u.getId(), u.getFullName(), u.getGrade()),
                                Collectors.toList())));

        List<ClassDto> sonuc = new ArrayList<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            List<ClassDto.ClassStudentDto> liste = ogrenciler.getOrDefault(id, List.of());
            sonuc.add(new ClassDto(id, (String) row[1], toInstant(row[2]), liste));
        }
        return sonuc;
    }

    /** createClass: Yeni sınıf açar. Aynı ad (büyük/küçük harf farkı gözetmeden) ikinci kez kullanılamaz. */
    @Transactional
    public ClassDto createClass(String name) {
        String ad = name == null ? "" : name.trim();
        if (ad.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sınıf adı boş bırakılamaz.");
        }
        if (ad.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sınıf adı en fazla 100 karakter olabilir.");
        }
        Number mevcut = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM classes WHERE LOWER(name) = LOWER(:ad)")
                .setParameter("ad", ad).getSingleResult();
        if (mevcut.longValue() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu adda bir sınıf zaten var: " + ad);
        }

        UUID id = UUID.randomUUID();
        Instant simdi = Instant.now();
        entityManager.createNativeQuery(
                "INSERT INTO classes (id, name, created_at) VALUES (:id, :ad, :now)")
                .setParameter("id", id).setParameter("ad", ad).setParameter("now", simdi)
                .executeUpdate();
        return new ClassDto(id, ad, simdi, List.of());
    }

    /** deleteClass: Sınıfı siler. İçindeki öğrenciler SİLİNMEZ, sınıfsız kalır. */
    @Transactional
    public void deleteClass(UUID classId) {
        int silinen = entityManager.createNativeQuery("DELETE FROM classes WHERE id = :id")
                .setParameter("id", classId).executeUpdate();
        if (silinen == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sınıf bulunamadı.");
        }
    }

    /** addStudent: Öğrenciyi sınıfa ekler. Öğrenci başka bir sınıftaysa yeni sınıfa taşınır. */
    @Transactional
    public void addStudent(UUID classId, UUID studentId) {
        sinifVarMi(classId);
        AppUser ogrenci = ogrenciGetir(studentId);
        ogrenci.setClassId(classId);
        userRepository.save(ogrenci);
    }

    /** removeStudent: Öğrenciyi sınıftan çıkarır. Yalnızca sınıf bağı kopar; verisi durur. */
    @Transactional
    public void removeStudent(UUID classId, UUID studentId) {
        sinifVarMi(classId);
        AppUser ogrenci = ogrenciGetir(studentId);
        if (!classId.equals(ogrenci.getClassId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci bu sınıfta değil.");
        }
        ogrenci.setClassId(null);
        userRepository.save(ogrenci);
    }

    private void sinifVarMi(UUID classId) {
        Number sayi = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM classes WHERE id = :id")
                .setParameter("id", classId).getSingleResult();
        if (sayi.longValue() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sınıf bulunamadı.");
        }
    }

    private AppUser ogrenciGetir(UUID studentId) {
        AppUser u = userRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Öğrenci bulunamadı."));
        if (u.getRole() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yalnızca öğrenciler sınıfa eklenebilir.");
        }
        return u;
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            case java.sql.Timestamp ts -> ts.toInstant();
            case java.time.OffsetDateTime odt -> odt.toInstant();
            default -> null;
        };
    }
}
