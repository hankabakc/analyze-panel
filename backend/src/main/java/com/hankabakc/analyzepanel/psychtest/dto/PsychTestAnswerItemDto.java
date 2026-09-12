package com.hankabakc.analyzepanel.psychtest.dto;

/**
 * PsychTestAnswerItemDto: Doldurulan testteki tek bir madde numarasını ve verilen cevabı (1..4) temsil eder.
 */
public record PsychTestAnswerItemDto(
        int itemNo,
        int answer
) {
}
