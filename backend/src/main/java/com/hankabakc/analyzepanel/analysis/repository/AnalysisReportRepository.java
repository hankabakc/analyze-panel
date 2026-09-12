package com.hankabakc.analyzepanel.analysis.repository;

import com.hankabakc.analyzepanel.analysis.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * AnalysisReportRepository: Analiz raporlarına ait veritabanı işlemlerini yönetir.
 */
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, UUID> {
    
    /**
     * Belirli bir öğrenciye ait tüm raporları kronolojik sırayla getirir.
     */
    List<AnalysisReport> findAllByStudentIdOrderByProcessedAtDesc(UUID studentId);
}
