package com.hankabakc.analyzepanel.studyplan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CreateStudyPlanItemRequest: Çalışma planı oluşturulurken tek bir konuya ait soru hedefini temsil eder.
 */
public record CreateStudyPlanItemRequest(
    @NotBlank(message = "Konu adı boş bırakılamaz.")
    String topicName,

    @NotNull(message = "Soru sayısı boş bırakılamaz.")
    @Min(value = 1, message = "Soru sayısı en az 1 olmalıdır.")
    Integer questionCount
) {}
