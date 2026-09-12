package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;

/**
 * SubmitPsychTestRequest: Öğrencinin testi tamamlama isteğinde gönderdiği cevaplar listesi.
 */
public record SubmitPsychTestRequest(
        List<PsychTestAnswerItemDto> answers
) {
}
