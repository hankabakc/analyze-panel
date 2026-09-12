package com.hankabakc.analyzepanel.membership.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** ClassStudentRequest: Sınıfa öğrenci ekleme isteği (T-040). */
public record ClassStudentRequest(
    @NotNull(message = "Öğrenci kimliği zorunludur.")
    UUID studentId
) {}
