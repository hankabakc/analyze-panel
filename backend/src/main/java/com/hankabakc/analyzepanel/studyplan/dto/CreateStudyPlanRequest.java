package com.hankabakc.analyzepanel.studyplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CreateStudyPlanRequest: Öğretmenin öğrenciye çalışma planı atama isteğidir.
 */
public record CreateStudyPlanRequest(
    @NotNull(message = "Öğrenci ID boş bırakılamaz.")
    UUID studentId,

    @NotNull(message = "Teslim tarihi boş bırakılamaz.")
    LocalDate dueDate,

    @NotEmpty(message = "En az bir çalışma planı kalemi eklenmelidir.")
    @Valid
    List<CreateStudyPlanItemRequest> items
) {}
