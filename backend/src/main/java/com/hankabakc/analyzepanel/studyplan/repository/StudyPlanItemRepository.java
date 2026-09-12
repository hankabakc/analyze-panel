package com.hankabakc.analyzepanel.studyplan.repository;

import com.hankabakc.analyzepanel.studyplan.entity.StudyPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * StudyPlanItemRepository: Çalışma planı kalemlerinin veritabanı erişim arayüzüdür.
 */
@Repository
public interface StudyPlanItemRepository extends JpaRepository<StudyPlanItem, UUID> {

    /**
     * Belirli bir plana ait tüm kalemleri getirir.
     *
     * @param planId Çalışma planı kimliği
     * @return Kalem listesi
     */
    List<StudyPlanItem> findAllByPlanId(UUID planId);

    /**
     * T-060 / ENG-06: N+1 problemini önlemek için birden fazla plana ait tüm kalemleri tek sorguda getirir.
     *
     * @param planIds Çalışma planı kimlikleri koleksiyonu
     * @return Kalem listesi
     */
    List<StudyPlanItem> findAllByPlanIdIn(java.util.Collection<UUID> planIds);

    /**
     * T-050D: Çağıran öğretmenin eşleştiği öğrencileri için tamamlanmış (completed_at != null)
     * fakat henüz öğretmen tarafından görülmemiş (teacher_seen_at IS NULL) kalemlerin sayısını
     * öğrenci bazında gruplayarak döner.
     *
     * @param teacherId Çağıran öğretmenin kimliği
     * @return [student_id, count] çiftleri
     */
    @Query(value = """
        SELECT sp.student_id, COUNT(spi.id)
        FROM study_plan_items spi
        JOIN study_plans sp ON spi.plan_id = sp.id
        JOIN student_teacher_pairings stp ON stp.student_id = sp.student_id AND stp.teacher_id = :teacherId
        WHERE sp.teacher_id = :teacherId
          AND spi.completed_at IS NOT NULL
          AND spi.teacher_seen_at IS NULL
        GROUP BY sp.student_id
        """, nativeQuery = true)
    List<Object[]> countUnseenCompletedItemsByTeacherId(@Param("teacherId") UUID teacherId);
}
