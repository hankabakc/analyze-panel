package com.hankabakc.analyzepanel.studyplan.repository;

import com.hankabakc.analyzepanel.studyplan.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * StudyPlanRepository: Çalışma planı varlıklarının veritabanı erişim arayüzüdür.
 */
@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    /**
     * Belirli bir öğrenciye ait çalışma planlarını oluşturulma tarihine göre azalan sırada getirir.
     *
     * @param studentId Öğrencinin kimliği
     * @return Çalışma planları listesi
     */
    List<StudyPlan> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
