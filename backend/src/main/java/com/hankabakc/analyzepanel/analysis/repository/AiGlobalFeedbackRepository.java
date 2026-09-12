package com.hankabakc.analyzepanel.analysis.repository;

import com.hankabakc.analyzepanel.analysis.entity.AiGlobalFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AiGlobalFeedbackRepository extends JpaRepository<AiGlobalFeedback, UUID> {
    Optional<AiGlobalFeedback> findByReportId(UUID reportId);
}
