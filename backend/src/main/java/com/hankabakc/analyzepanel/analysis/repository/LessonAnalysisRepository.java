package com.hankabakc.analyzepanel.analysis.repository;

import com.hankabakc.analyzepanel.analysis.entity.LessonAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LessonAnalysisRepository extends JpaRepository<LessonAnalysis, UUID> {
    List<LessonAnalysis> findAllByReportId(UUID reportId);
}
