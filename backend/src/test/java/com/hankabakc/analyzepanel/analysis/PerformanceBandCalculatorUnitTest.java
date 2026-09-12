package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.service.PerformanceBandCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PerformanceBandCalculatorUnitTest: Konu başarı oranına göre performans bandı (STRONG, MEDIUM, WEAK)
 * hesaplamasını ve yapılandırılabilir eşiklerin doğruluğunu sınar (T-030C).
 */
class PerformanceBandCalculatorUnitTest {

    private final PerformanceBandCalculator defaultCalculator = new PerformanceBandCalculator(85.0, 50.0);

    @Test
    @DisplayName("Başarı oranı >= %85 olan konular STRONG bandında olmalıdır")
    void testCalculateBand_StrongAboveOrEqual85() {
        assertEquals("STRONG", defaultCalculator.calculateBand(10, 10)); // %100
        assertEquals("STRONG", defaultCalculator.calculateBand(20, 17)); // %85
        assertEquals("STRONG", defaultCalculator.calculateBand(11, 10)); // %90.9
    }

    @Test
    @DisplayName("Başarı oranı %50 ile %85 arasında olan konular MEDIUM bandında olmalıdır")
    void testCalculateBand_MediumBetween50And85() {
        assertEquals("MEDIUM", defaultCalculator.calculateBand(20, 16)); // %80
        assertEquals("MEDIUM", defaultCalculator.calculateBand(10, 5));  // %50
        assertEquals("MEDIUM", defaultCalculator.calculateBand(6, 4));   // %66.7
    }

    @Test
    @DisplayName("Başarı oranı < %50 olan konular WEAK bandında olmalıdır")
    void testCalculateBand_WeakBelow50() {
        assertEquals("WEAK", defaultCalculator.calculateBand(10, 4));  // %40
        assertEquals("WEAK", defaultCalculator.calculateBand(5, 0));   // %0
        assertEquals("WEAK", defaultCalculator.calculateBand(3, 1));   // %33.3
    }

    @Test
    @DisplayName("Soru sayısı 0 veya null olduğunda WEAK dönmelidir")
    void testCalculateBand_NullOrZeroTotal_ReturnsWeak() {
        assertEquals("WEAK", defaultCalculator.calculateBand(0, 0));
        assertEquals("WEAK", defaultCalculator.calculateBand(null, 0));
        assertEquals("WEAK", defaultCalculator.calculateBand(0, null));
        assertEquals("WEAK", defaultCalculator.calculateBand(null, null));
    }

    @Test
    @DisplayName("Yapılandırma kanıtı: Eşikler değiştirildiğinde hesaplanan bant buna göre güncellenmelidir")
    void testConfigurableThresholds_CustomValuesApplied() {
        // thresholdStrong = 95.0, thresholdMedium = 70.0
        PerformanceBandCalculator customCalculator = new PerformanceBandCalculator(95.0, 70.0);

        // %90 normalde STRONG iken özel yapılandırmada MEDIUM olmalıdır
        assertEquals("MEDIUM", customCalculator.calculateBand(10, 9)); // %90
        // %95 STRONG olmalıdır
        assertEquals("STRONG", customCalculator.calculateBand(20, 19)); // %95
        // %60 normalde MEDIUM iken özel yapılandırmada WEAK olmalıdır
        assertEquals("WEAK", customCalculator.calculateBand(10, 6)); // %60
    }
}
