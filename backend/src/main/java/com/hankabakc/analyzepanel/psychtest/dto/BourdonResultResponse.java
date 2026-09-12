package com.hankabakc.analyzepanel.psychtest.dto;

import java.time.Instant;
import java.util.UUID;

public record BourdonResultResponse(
        UUID assignmentId,
        String studentName,
        Instant startedAt,
        Instant submittedAt,
        int durationSeconds,
        boolean timedOut,
        BourdonBlockScoreDto block1,
        BourdonBlockScoreDto block2,
        BourdonBlockScoreDto block3,
        int totalCorrect,
        int totalOmitted,
        int totalIncorrect,
        int totalTargets,
        String observationNote
) {
}
