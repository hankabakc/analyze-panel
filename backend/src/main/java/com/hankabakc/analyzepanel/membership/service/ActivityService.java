package com.hankabakc.analyzepanel.membership.service;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.membership.dto.ActivityDto;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ActivityService: Kullanıcı aktifliğini kaydeder ve yöneticiye özetler (T-037 / S-017 → B).
 *
 * <p>Kaydedilen iki şey:</p>
 * <ol>
 *   <li><b>Son giriş zamanı</b> — her başarılı e-posta + şifre girişinde.</li>
 *   <li><b>Rapor görüntüleme</b> — kim, hangi öğrencinin, hangi raporuna, ne zaman baktı.</li>
 * </ol>
 *
 * <p><b>Kayıt asıl işi bozmaz.</b> Yazma kendi transaction'ında çalışır ({@code REQUIRES_NEW}) ve
 * hata <b>çağıran tarafta</b> yakalanır. Hatayı bu sınıfın içinde yutmak yetmez: iç transaction
 * geri alınmaya işaretlendiğinde istisna, metot dönerken transaction katmanından
 * ({@code UnexpectedRollbackException}) yeniden fırlar ve dış işlemi düşürür. Bu tuzağa proje
 * daha önce de düşmüştü (T-001C / B-5). Bu yüzden {@code recordLogin} / {@code recordReportView} sarmalayıcıları
 * <b>transaction dışıdır</b> ve gerçek yazmayı çağırıp hatasını yutar (ENG-03 §3).</p>
 *
 * <p><b>Saklama süresi (Kullanıcı Kararı, 2026-09-08 / T-037B / S-005):</b> Aktiflik ve rapor görüntüleme
 * kayıtları kullanıcının açık kararıyla şimdilik <b>süresiz</b> saklanmaktadır ("Şu anlık silmek gibi bir
 * niyetimiz yok. İleride değiştirebiliriz, ama şu an buna karar vermeyeceğim."). Bu geçici bir karardır.
 * <b>Açık Kalan Borç:</b> Kullanıcıların çoğu 18 yaş altı olduğu için süresiz saklama APP-03 §4 ile
 * çelişmektedir; sistem yayına çıkmadan önce çocuk verisi minimizasyonu gereği saklama süresi yeniden
 * ele alınmalı ve süresi dolan kayıtları temizleyen bir mekanizma eklenmelidir.</p>
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final EntityManager entityManager;
    private final AppUserRepository userRepository;
    private final org.springframework.context.ApplicationContext applicationContext;

    public ActivityService(EntityManager entityManager, AppUserRepository userRepository,
                           org.springframework.context.ApplicationContext applicationContext) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.applicationContext = applicationContext;
    }

    /** recordLogin: Başarılı girişte son giriş zamanını günceller. Hatası girişi düşürmez. */
    public void recordLogin(UUID userId) {
        if (userId == null) return;
        try {
            self().writeLogin(userId);
        } catch (Exception e) {
            log.warn("[T-037] Son giriş zamanı kaydedilemedi (giriş etkilenmedi): {}", e.getMessage());
        }
    }

    /** Gerçek yazma: kendi transaction'ında çalışır, hatası çağırana bırakılır. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeLogin(UUID userId) {
        entityManager.createNativeQuery("UPDATE app_users SET last_login_at = :now WHERE id = :id")
                .setParameter("now", Instant.now())
                .setParameter("id", userId)
                .executeUpdate();
    }

    /** recordReportView: Bir raporun görüntülendiğini kaydeder. Hatası raporu açmayı engellemez. */
    public void recordReportView(UUID viewerId, UUID studentId, UUID reportId) {
        if (viewerId == null || studentId == null || reportId == null) return;
        try {
            self().writeReportView(viewerId, studentId, reportId);
        } catch (Exception e) {
            log.warn("[T-037] Rapor görüntüleme kaydedilemedi (görüntüleme etkilenmedi): {}", e.getMessage());
        }
    }

    /** Gerçek yazma: kendi transaction'ında çalışır, hatası çağırana bırakılır. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeReportView(UUID viewerId, UUID studentId, UUID reportId) {
        entityManager.createNativeQuery(
                    "INSERT INTO report_views (id, viewer_id, student_id, report_id, viewed_at) "
                            + "VALUES (:id, :viewer, :student, :report, :now)")
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("viewer", viewerId)
                    .setParameter("student", studentId)
                    .setParameter("report", reportId)
                .setParameter("now", Instant.now())
                .executeUpdate();
    }

    /**
     * self: Kendi proxy'sini verir.
     *
     * <p>Aynı sınıf içinden doğrudan çağrı Spring proxy'sini atlar ve {@code REQUIRES_NEW}
     * çalışmaz; bu yüzden yazma metodu proxy üzerinden çağrılır.</p>
     */
    private ActivityService self() {
        return applicationContext.getBean(ActivityService.class);
    }

    /**
     * getActivityOverview: Yönetici ekranı için her kullanıcının aktiflik özeti.
     *
     * <p>Öğretmenler için ayrıca <b>eşleştiği her öğrenciye en son ne zaman baktığı</b> döner;
     * hiç bakmadıysa {@code lastViewedAt} boştur — "bakmamış" bilgisi asıl sorulan şeydir.</p>
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ActivityDto> getActivityOverview() {
        List<AppUser> users = userRepository.findAll();
        if (users.isEmpty()) return List.of();

        Map<UUID, Object[]> viewStats = ((List<Object[]>) entityManager.createNativeQuery(
                "SELECT viewer_id, COUNT(*), MAX(viewed_at) FROM report_views GROUP BY viewer_id")
                .getResultList()).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> r));

        // Öğretmen -> öğrenci bakış özeti: eşleşen her öğrenci için son bakış (yoksa null)
        List<Object[]> pairRows = entityManager.createNativeQuery(
                "SELECT p.teacher_id, p.student_id, "
                        + "       (SELECT MAX(rv.viewed_at) FROM report_views rv "
                        + "         WHERE rv.viewer_id = p.teacher_id AND rv.student_id = p.student_id) "
                        + "FROM student_teacher_pairings p")
                .getResultList();

        Map<UUID, String> nameById = users.stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));

        Map<UUID, List<ActivityDto.StudentViewDto>> byTeacher = new java.util.HashMap<>();
        for (Object[] row : pairRows) {
            UUID teacherId = (UUID) row[0];
            UUID studentId = (UUID) row[1];
            byTeacher.computeIfAbsent(teacherId, k -> new ArrayList<>())
                    .add(new ActivityDto.StudentViewDto(studentId, nameById.get(studentId), toInstant(row[2])));
        }

        List<ActivityDto> result = new ArrayList<>();
        for (AppUser u : users) {
            Object[] stats = viewStats.get(u.getId());
            long viewCount = stats != null ? ((Number) stats[1]).longValue() : 0L;
            Instant lastView = stats != null ? toInstant(stats[2]) : null;
            result.add(new ActivityDto(
                    u.getId(),
                    u.getFullName(),
                    u.getRole().name(),
                    u.getLastLoginAt(),
                    viewCount,
                    lastView,
                    byTeacher.getOrDefault(u.getId(), List.of())));
        }
        result.sort(java.util.Comparator.comparing(ActivityDto::role)
                .thenComparing(a -> a.fullName() == null ? "" : a.fullName(), String.CASE_INSENSITIVE_ORDER));
        return result;
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
