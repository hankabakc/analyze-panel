package com.hankabakc.analyzepanel.studyplan.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * StudyPlanItemResponse: Çalışma planı kaleminin dışa dönen DTO temsilidir.
 */
public record StudyPlanItemResponse(
    UUID id,
    UUID planId,
    String topicName,
    Integer questionCount,
    Instant completedAt,
    Instant teacherSeenAt
) {}
