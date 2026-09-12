package com.hankabakc.analyzepanel.psychtest.dto;

public record BourdonBlockScoreDto(
        int blockNumber,
        int correct,
        int omitted,
        int incorrect,
        int targetCount
) {
}
