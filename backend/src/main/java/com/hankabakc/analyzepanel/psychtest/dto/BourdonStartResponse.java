package com.hankabakc.analyzepanel.psychtest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BourdonStartResponse(
        UUID assignmentId,
        Instant startedAt,
        int durationSeconds,
        int remainingSeconds,
        List<String> grid
) {
    /**
     * Geriye dönük uyumlu kurucu (Pure Java politikası).
     */
    public BourdonStartResponse(UUID assignmentId, Instant startedAt, int durationSeconds, List<String> grid) {
        this(assignmentId, startedAt, durationSeconds, durationSeconds, grid);
    }
}
