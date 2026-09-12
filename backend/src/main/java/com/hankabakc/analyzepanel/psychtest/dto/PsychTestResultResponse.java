package com.hankabakc.analyzepanel.psychtest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * PsychTestResultResponse: Yönetici ve yetkili öğretmenin öğrenci test sonuçlarını,
 * puanlarını ve kaygı seviyesi etiketlerini gördüğü DTO (T-061 / S-024).
 * APP-03 §4: Yalnızca yöneticiye ve eşleştiği öğretmene açıktır; öğrenciye dönülmez.
 */
public record PsychTestResultResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String testCode,
        String status,
        Instant assignedAt,
        Instant completedAt,
        Integer stateScore,
        Integer traitScore,
        String stateLevel,
        String traitLevel,
        Integer bourdonTotalCorrect,
        Integer bourdonTotalOmitted,
        Integer bourdonTotalIncorrect,
        Integer bourdonDurationSeconds,
        Boolean bourdonTimedOut
) {
    public PsychTestResultResponse(
            UUID id,
            UUID studentId,
            String studentName,
            String testCode,
            String status,
            Instant assignedAt,
            Instant completedAt,
            Integer stateScore,
            Integer traitScore,
            String stateLevel,
            String traitLevel
    ) {
        this(id, studentId, studentName, testCode, status, assignedAt, completedAt, stateScore, traitScore, stateLevel, traitLevel, null, null, null, null, null);
    }
}
