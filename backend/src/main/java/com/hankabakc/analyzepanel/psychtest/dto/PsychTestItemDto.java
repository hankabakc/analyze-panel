package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;

/**
 * PsychTestItemDto: Bir test maddesinin numarasını, metnini, bölümünü (STATE/TRAIT) ve seçeneklerini taşır.
 */
public record PsychTestItemDto(
        int itemNo,
        String text,
        String section,
        List<PsychTestOptionDto> options
) {
}
