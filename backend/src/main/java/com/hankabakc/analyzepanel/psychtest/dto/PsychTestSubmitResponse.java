package com.hankabakc.analyzepanel.psychtest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * PsychTestSubmitResponse: Test tamamlandıktan sonra öğrenciye dönen onay yanıtı.
 * APP-03 §4: Öğrenciye puan dönülmez, yalnızca tamamlandı durumu ve zamanı bildirilir.
 */
public record PsychTestSubmitResponse(
        UUID id,
        String status,
        Instant completedAt,
        String message
) {
}
