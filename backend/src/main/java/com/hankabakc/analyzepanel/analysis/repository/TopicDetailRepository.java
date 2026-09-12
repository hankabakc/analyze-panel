package com.hankabakc.analyzepanel.analysis.repository;

import com.hankabakc.analyzepanel.analysis.entity.TopicDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TopicDetailRepository extends JpaRepository<TopicDetail, UUID> {
    List<TopicDetail> findAllByLessonAnalysisId(UUID lessonAnalysisId);

    /**
     * T-050A: Bir öğrencinin ayrıştırılmış tüm karnelerindeki benzersiz konu adlarını getirir.
     * Konu uydurmayı engellemek (APP-01 §2.1, §2.7) için kullanılır.
     *
     * @param studentId Öğrencinin kimliği
     * @return Ayrıştırılmış benzersiz konu adları listesi
     */
    @Query("""
        SELECT DISTINCT td.topicName 
        FROM TopicDetail td 
        JOIN LessonAnalysis la ON td.lessonAnalysisId = la.id 
        JOIN AnalysisReport ar ON la.reportId = ar.id 
        WHERE ar.studentId = :studentId
    """)
    List<String> findDistinctTopicNamesByStudentId(@Param("studentId") UUID studentId);
}
