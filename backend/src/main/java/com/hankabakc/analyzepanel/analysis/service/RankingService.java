package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.ClassRankingGroupDto;
import com.hankabakc.analyzepanel.analysis.dto.RankingEntryDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RankingService: Denemelere göre sınıf bazlı sıralama üretir (T-038 / T-038A / T-038B).
 *
 * <p><b>Sınıf bazlı izolasyon:</b> Sıralama kurum geneli değil, {@code app_users.class_id}
 * alanına göre sınıf bazında gruplanır (T-038B). Öğrenci yalnızca kendi sınıfına ait grubu alır;
 * başka sınıfın satırları yanıtta hiç yer almaz (APP-02 §1).</p>
 *
 * <p><b>Sınıfsız öğrenciler:</b> Henüz sınıfa atanmamış öğrenciler "Sınıfsız" başlığı altında
 * ayrı bir grup olarak sunulur; sınıfsız öğrenci kendi grubunu görür.</p>
 *
 * <p><b>Grup içi sıra:</b> Sıra numaraları (rank) her grup içinde 1'den başlar; maskeleme etiketi
 * ("1. Öğrenci") de grup içi sıraya bağlanır.</p>
 *
 * <p><b>Kim gerçek adı görür:</b></p>
 * <ul>
 *   <li><b>Öğrenci</b> — yalnızca kendi sınıfında yalnızca kendi adını; diğer herkes maskeli (APP-02 §1).</li>
 *   <li><b>Öğretmen</b> — tüm sınıfları ve sınıfsızları gerçek öğrenci adlarıyla görür (T-038A).</li>
 *   <li><b>Yönetici</b> — tüm sınıfları ve sınıfsızları gerçek öğrenci adlarıyla görür.</li>
 * </ul>
 */
@Service
public class RankingService {

    private final EntityManager entityManager;
    private final SecurityUtils securityUtils;
    private final AppUserRepository userRepository;

    public RankingService(EntityManager entityManager, SecurityUtils securityUtils, AppUserRepository userRepository) {
        this.entityManager = entityManager;
        this.securityUtils = securityUtils;
        this.userRepository = userRepository;
    }

    /**
     * getRanking: Onaylı denemelerin net ortalamasına göre sınıf bazlı sıralanmış listeyi verir.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ClassRankingGroupDto> getRanking() {
        AppUser bakan = securityUtils.getCurrentUser();
        if (bakan == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT ar.student_id, AVG(es.total_score), COUNT(es.id) "
                        + "FROM exam_summaries es "
                        + "JOIN analysis_reports ar ON es.report_id = ar.id "
                        + "WHERE ar.status = 'APPROVED' AND es.total_score IS NOT NULL "
                        + "GROUP BY ar.student_id")
                .getResultList();
        if (rows.isEmpty()) return List.of();

        // 1. Sınıf adlarını veritabanından çözümle
        List<Object[]> classRows = entityManager.createNativeQuery(
                "SELECT id, name FROM classes")
                .getResultList();
        Map<UUID, String> classNames = new HashMap<>();
        for (Object[] cr : classRows) {
            classNames.put((UUID) cr[0], (String) cr[1]);
        }

        // T-076: N+1 önleme — Tüm öğrenci kimliklerini topla ve tek sorguda toplu çek
        Set<UUID> studentIds = new HashSet<>();
        for (Object[] r : rows) {
            studentIds.add((UUID) r[0]);
        }
        Map<UUID, AppUser> studentMap = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        // 2. Öğrenci net ortalamalarını topla ve class_id'ye göre grupla
        record Satir(UUID studentId, UUID classId, String fullName, double average, int examCount) {}
        Map<UUID, List<Satir>> gruplanmisSatirlar = new LinkedHashMap<>();

        for (Object[] r : rows) {
            UUID studentId = (UUID) r[0];
            double ortalama = Math.round(((Number) r[1]).doubleValue() * 100.0) / 100.0;
            int deneme = ((Number) r[2]).intValue();
            AppUser student = studentMap.get(studentId);
            String ad = (student != null && student.getFullName() != null) ? student.getFullName() : "";
            UUID classId = student != null ? student.getClassId() : null;

            gruplanmisSatirlar.computeIfAbsent(classId, k -> new ArrayList<>())
                    .add(new Satir(studentId, classId, ad, ortalama, deneme));
        }

        // 3. Her sınıf grubunu kendi içinde sırala ve DTO'ya dönüştür
        List<ClassRankingGroupDto> tumGruplar = new ArrayList<>();
        for (Map.Entry<UUID, List<Satir>> entry : gruplanmisSatirlar.entrySet()) {
            UUID classId = entry.getKey();
            List<Satir> satirlar = entry.getValue();

            satirlar.sort(Comparator.comparingDouble(Satir::average).reversed()
                    .thenComparing(Comparator.comparingInt(Satir::examCount).reversed())
                    .thenComparing(Satir::fullName, String.CASE_INSENSITIVE_ORDER));

            List<RankingEntryDto> entries = new ArrayList<>();
            for (int i = 0; i < satirlar.size(); i++) {
                Satir s = satirlar.get(i);
                int sira = i + 1;
                boolean kendisi = bakan.getId().equals(s.studentId());
                boolean gercekAd = switch (bakan.getRole()) {
                    case MANAGER, TEACHER -> true;
                    case STUDENT -> kendisi;
                    default -> false;
                };
                entries.add(new RankingEntryDto(
                        sira,
                        gercekAd ? s.fullName() : sira + ". Öğrenci",
                        s.average(),
                        s.examCount(),
                        bakan.getRole() == UserRole.STUDENT && kendisi
                ));
            }

            String className = classId != null ? classNames.getOrDefault(classId, "Bilinmeyen Sınıf") : "Sınıfsız";
            tumGruplar.add(new ClassRankingGroupDto(classId, className, entries));
        }

        // 4. Grupları sırala: Adlandırılmış sınıflar alfabetik (LOWER), "Sınıfsız" en sonda
        tumGruplar.sort((g1, g2) -> {
            if (g1.classId() == null && g2.classId() == null) return 0;
            if (g1.classId() == null) return 1;
            if (g2.classId() == null) return -1;
            return g1.className().compareToIgnoreCase(g2.className());
        });

        // 5. Yetki Filtresi: Öğrenci yalnızca kendi sınıf grubunu alır
        if (bakan.getRole() == UserRole.STUDENT) {
            UUID ogrenciClassId = bakan.getClassId();
            return tumGruplar.stream()
                    .filter(g -> Objects.equals(g.classId(), ogrenciClassId))
                    .toList();
        }

        // Öğretmen ve Yönetici tüm grupları görür
        return tumGruplar;
    }
}
