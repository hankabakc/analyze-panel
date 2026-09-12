package com.hankabakc.analyzepanel.studyplan.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * StudyPlanResponse: Çalışma planının ve altındaki kalemlerin dışa dönen DTO temsilidir.
 */
public record StudyPlanResponse(
    UUID id,
    UUID studentId,
    UUID teacherId,
    LocalDate dueDate,
    Instant createdAt,
    List<StudyPlanItemResponse> items
) {}
