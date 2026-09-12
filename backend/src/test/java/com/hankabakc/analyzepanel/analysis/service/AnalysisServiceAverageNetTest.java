package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AnalysisServiceAverageNetTest: Sınav net ortalamasının puanı okunamamış sınavı
 * ortalamaya katmadığını doğrular (T-028B / B-67).
 */
class AnalysisServiceAverageNetTest {

    private static AnalysisResponse.ExamSummaryDto exam(String name, Double score) {
        return new AnalysisResponse.ExamSummaryDto(
                name, "01.01.2026", score == null ? null : BigDecimal.valueOf(score));
    }

    @Test
    @DisplayName("T-028B: Puanı okunamamış sınav ortalamaya 0 net olarak katılmaz")
    void testAverageNet_SkipsNullScoresInsteadOfCountingThemAsZero() {
        List<AnalysisResponse.ExamSummaryDto> exams = List.of(
                exam("Deneme 1", 80.0),
                exam("Deneme 2", null),
                exam("Deneme 3", 90.0));

        OptionalDouble result = AnalysisService.averageNet(exams);

        assertTrue(result.isPresent());
        // Doğrusu (80 + 90) / 2 = 85.0. Eski davranış (80 + 0 + 90) / 3 = 56.67 veriyordu.
        assertEquals(85.0, result.getAsDouble(),
                "Null puanlı sınav ortalamaya katılmamalıdır; eski davranış 56.67 üretiyordu.");
    }

    @Test
    @DisplayName("T-028B: Hiç geçerli puan yoksa ortalama boş döner (hedef karşılaştırması üretilmez)")
    void testAverageNet_AllNullScoresReturnsEmpty() {
        assertTrue(AnalysisService.averageNet(List.of(exam("Deneme 1", null))).isEmpty());
        assertTrue(AnalysisService.averageNet(List.of()).isEmpty());
        assertTrue(AnalysisService.averageNet(null).isEmpty());
    }

    @Test
    @DisplayName("T-028B: Tüm puanlar geçerliyse ortalama olduğu gibi hesaplanır")
    void testAverageNet_AllScoresPresent() {
        OptionalDouble result = AnalysisService.averageNet(List.of(exam("A", 80.0), exam("B", 90.0)));
        assertEquals(85.0, result.getAsDouble());
    }
}
