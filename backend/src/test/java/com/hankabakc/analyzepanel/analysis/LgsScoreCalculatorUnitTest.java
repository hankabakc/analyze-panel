package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import com.hankabakc.analyzepanel.analysis.service.LgsScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LgsScoreCalculatorUnitTest {

    private final LgsScoreCalculator calculator = new LgsScoreCalculator(4.44, 100.0);

    @Test
    @DisplayName("T-028: Ortalama net doğru şekilde LGS puanına dönüştürülür (100 + Net * 4.44)")
    void testCalculatePredictedScore() {
        // 80.40 net -> 100 + 80.40 * 4.44 = 456.976 -> 457.0 puan
        assertEquals(457.0, calculator.calculatePredictedScore(80.40));

        // 0 net -> 100.0 puan
        assertEquals(100.0, calculator.calculatePredictedScore(0.0));

        // 50 net -> 100 + 50 * 4.44 = 322.0 puan
        assertEquals(322.0, calculator.calculatePredictedScore(50.0));
    }

    @Test
    @DisplayName("T-028: Hedef okul karşılaştırması LGS puan ölçeğinde doğru uyum ve mesafe üretir (500 puan hedef, 80.4 net -> %91 uyum, 43.0 puan, ≈9.7 net mesafe)")
    void testCompareWithTarget_UnderTarget() {
        AnalysisResponse.TargetComparisonDto result = calculator.compareWithTarget(
                "Kabataş Erkek Lisesi",
                BigDecimal.valueOf(500.0),
                80.40
        );

        assertNotNull(result);
        assertEquals("Kabataş Erkek Lisesi", result.schoolName());
        assertEquals(BigDecimal.valueOf(500.0), result.targetScore());
        assertEquals(80.40, result.averageNet());
        assertEquals(457.0, result.predictedLgsScore());
        assertEquals(91, result.compatibilityPercent(), "80.40 net ile 500 puanlık hedef için uyum %91 olmalıdır (eski %16 hatası çözüldü).");
        assertEquals(43.0, result.scoreGap(), "Hedefe kalan puan mesafesi 43.0 olmalıdır (eski 419.6 hatası çözüldü).");
        assertEquals(9.7, result.netGap(), "Hedefe kalan net mesafesi 9.7 net olmalıdır.");
    }

    @Test
    @DisplayName("T-028: Öğrenci hedef okul puanının üzerindeyse uyum %100 tavanına oturur ve mesafe negatif (üzerinde) olur")
    void testCompareWithTarget_AboveTarget() {
        AnalysisResponse.TargetComparisonDto result = calculator.compareWithTarget(
                "Anadolu Lisesi",
                BigDecimal.valueOf(400.0),
                80.40
        );

        assertNotNull(result);
        assertEquals(457.0, result.predictedLgsScore());
        assertEquals(100, result.compatibilityPercent());
        assertEquals(-57.0, result.scoreGap());
        assertEquals(-12.8, result.netGap());
    }

    @Test
    @DisplayName("T-028 Yapılandırma Kanıtı: analysis.lgs.puan-per-net=10 olduğunda tahmini puan 904.0 ve uyum %100 olur")
    void testConfigProof_PuanPerNet10() {
        LgsScoreCalculator calc10 = new LgsScoreCalculator(10.0, 100.0);
        assertEquals(904.0, calc10.calculatePredictedScore(80.40));

        AnalysisResponse.TargetComparisonDto result = calc10.compareWithTarget("Kabataş Erkek Lisesi", BigDecimal.valueOf(500.0), 80.40);
        assertNotNull(result);
        assertEquals(904.0, result.predictedLgsScore());
        assertEquals(100, result.compatibilityPercent(), "Tahmini puan hedefi aştığında uyum %100 tavanına oturur.");
        assertEquals(-404.0, result.scoreGap());
    }

    @Test
    @DisplayName("T-028B: Sıfır veya negatif puan katsayısı açılışta reddedilir (netGap bölmesi Infinity üretmez)")
    void testConstructor_RejectsNonPositivePuanPerNet() {
        IllegalArgumentException sifir = assertThrows(IllegalArgumentException.class,
                () -> new LgsScoreCalculator(0.0, 100.0));
        assertTrue(sifir.getMessage().contains("puan-per-net"), "Hata mesajı hangi ayarın hatalı olduğunu söylemelidir.");

        assertThrows(IllegalArgumentException.class, () -> new LgsScoreCalculator(-1.0, 100.0));
    }

    @Test
    @DisplayName("T-028: Hedef okul seçili değilse karşılaştırma null döner")
    void testCompareWithTarget_NullTarget() {
        assertNull(calculator.compareWithTarget(null, BigDecimal.valueOf(500.0), 80.40));
        assertNull(calculator.compareWithTarget("Okul", null, 80.40));
        assertNull(calculator.compareWithTarget("Okul", BigDecimal.ZERO, 80.40));
    }
}
