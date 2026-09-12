package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.Map;

/**
 * UnseenPsychTestCountsResponse: Henüz incelenmemiş / görülmemiş tamamlanmış psikolojik test sayıları yanıtı (T-062).
 * 
 * Standartlar:
 * - Immutability: DTO yapısı Java record tipindedir.
 * - ENG-06: Toplu sayım verisi tek yanıt ile iletilir.
 *
 * @param totalUnseen Toplam görülmemiş tamamlanmış test sayısı (öğretmen için eşleştiği öğrencilerin toplamı, yönetici için sistem genelindeki toplam).
 * @param studentCounts Öğrenci kimliği -> görülmemiş tamamlanmış test adedi haritası.
 */
public record UnseenPsychTestCountsResponse(
        long totalUnseen,
        Map<String, Long> studentCounts
) {
}
