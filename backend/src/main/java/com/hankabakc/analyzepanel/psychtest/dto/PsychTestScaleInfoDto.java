package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;

/**
 * PsychTestScaleInfoDto: Testin genel bilgilerini ve madde listesini taşır.
 */
public record PsychTestScaleInfoDto(
        String testCode,
        String title,
        String description,
        List<PsychTestItemDto> items
) {
}
