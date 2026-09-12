package com.hankabakc.analyzepanel.psychtest.repository;

import com.hankabakc.analyzepanel.psychtest.entity.PsychResultView;
import com.hankabakc.analyzepanel.psychtest.entity.PsychResultViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * PsychResultViewRepository: Görülmüş psikolojik test sonuçlarını izleme ve sayım sorguları (T-062).
 * 
 * Standartlar:
 * - ENG-06: Sayım sorguları tek sorguda çözülür, N+1 sorgusu oluşmaz.
 * - ENG-07 §1.1: Okuma sorguları kesinlikle yan etki üretmez.
 * - ON CONFLICT DO NOTHING: Mükerrer görüldü çağrıları ilk görüldü zaman damgasını korur.
 */
@Repository
public interface PsychResultViewRepository extends JpaRepository<PsychResultView, PsychResultViewId> {

    /**
     * countUnseenCompletedForTeacher: Öğretmenin eşleştiği öğrencilerin tamamlanmış fakat henüz
     * öğretmen tarafından görülmemiş test sayılarını tek sorguda döner.
     */
    @Query(value = """
        SELECT stp.student_id, COUNT(CASE WHEN pta.id IS NOT NULL AND prv.assignment_id IS NULL THEN 1 END)
        FROM student_teacher_pairings stp
        LEFT JOIN psych_test_assignments pta ON pta.student_id = stp.student_id AND pta.status = 'COMPLETED'
        LEFT JOIN psych_result_views prv ON prv.assignment_id = pta.id AND prv.viewer_id = :viewerId
        WHERE stp.teacher_id = :viewerId
        GROUP BY stp.student_id
        """, nativeQuery = true)
    List<Object[]> countUnseenCompletedForTeacher(@Param("viewerId") UUID viewerId);

    /**
     * countUnseenCompletedForManager: Yöneticinin sistem genelindeki tüm öğrenciler için henüz görmediği
     * tamamlanmış test sayılarını öğrenci bazında gruplayarak döner.
     */
    @Query(value = """
        SELECT pta.student_id, COUNT(pta.id)
        FROM psych_test_assignments pta
        LEFT JOIN psych_result_views prv ON prv.assignment_id = pta.id AND prv.viewer_id = :viewerId
        WHERE pta.status = 'COMPLETED' AND prv.assignment_id IS NULL
        GROUP BY pta.student_id
        """, nativeQuery = true)
    List<Object[]> countUnseenCompletedForManager(@Param("viewerId") UUID viewerId);

    /**
     * markStudentResultsAsSeen: Belirli bir öğrencinin tamamlanmış tüm testlerini çağıran kullanıcı için görüldü işaretler.
     * ON CONFLICT DO NOTHING sayesinde ilk görülme zaman damgası korunur.
     */
    @Modifying
    @Query(value = """
        INSERT INTO psych_result_views (assignment_id, viewer_id, seen_at)
        SELECT pta.id, :viewerId, CURRENT_TIMESTAMP
        FROM psych_test_assignments pta
        WHERE pta.student_id = :studentId AND pta.status = 'COMPLETED'
        ON CONFLICT (assignment_id, viewer_id) DO NOTHING
        """, nativeQuery = true)
    int markStudentResultsAsSeen(@Param("studentId") UUID studentId, @Param("viewerId") UUID viewerId);

    /**
     * markSpecificResultsAsSeen: Yalnızca belirtilen ve COMPLETED durumundaki atama kimliklerini görüldü işaretler (T-062B).
     * Var olmayan veya COMPLETED olmayan atamalar INSERT edilmez (yok sayılır).
     * ON CONFLICT DO NOTHING sayesinde ilk görülme zaman damgası korunur.
     */
    @Modifying
    @Query(value = """
        INSERT INTO psych_result_views (assignment_id, viewer_id, seen_at)
        SELECT pta.id, :viewerId, CURRENT_TIMESTAMP
        FROM psych_test_assignments pta
        WHERE pta.id IN (:assignmentIds) AND pta.status = 'COMPLETED'
        ON CONFLICT (assignment_id, viewer_id) DO NOTHING
        """, nativeQuery = true)
    int markSpecificResultsAsSeen(@Param("assignmentIds") List<UUID> assignmentIds, @Param("viewerId") UUID viewerId);
}

