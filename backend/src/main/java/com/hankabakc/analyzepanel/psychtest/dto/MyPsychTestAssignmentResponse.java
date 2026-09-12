package com.hankabakc.analyzepanel.psychtest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * MyPsychTestAssignmentResponse: Öğrencinin kendisine atanan testleri listelediği yanıt DTO'su.
 * APP-03 §4 İstisnası: Öğrenci kendi puanlarını asla görmez, bu nedenle stateScore ve traitScore alanları bu yanıtta yer almaz.
 */
public record MyPsychTestAssignmentResponse(
        UUID id,
        String testCode,
        String testTitle,
        Instant assignedAt,
        String status,
        Instant completedAt
) {
}
