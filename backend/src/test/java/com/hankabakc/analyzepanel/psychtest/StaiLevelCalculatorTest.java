package com.hankabakc.analyzepanel.psychtest;

import com.hankabakc.analyzepanel.psychtest.service.PsychTestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * StaiLevelCalculatorTest: T-061 STAI kaygı seviyesi hesaplayıcısı için sınır değer ve yapılandırma testleri.
 * APP-01 §2.1: Seviye hesabı sunucu tarafında tek merkezde yapılır.
 * S-024 / T-061:
 *  - Düşük (LOW): 20–39 (varsayılan)
 *  - Orta (MEDIUM): 40–59 (varsayılan)
 *  - Yüksek (HIGH): 60–80 (varsayılan)
 */
public class StaiLevelCalculatorTest {

    private PsychTestService createService(int mediumMin, int highMin) {
        return new PsychTestService(
                null, null, null, null, null, null, null, null, null, mediumMin, highMin
        );
    }

    @ParameterizedTest(name = "Varsayılan eşiklerle puan={0} -> beklenen seviye={1}")
    @CsvSource({
            "20, LOW",
            "39, LOW",
            "40, MEDIUM",
            "59, MEDIUM",
            "60, HIGH",
            "80, HIGH",
            "0, LOW",
            "100, HIGH"
    })
    @DisplayName("Sınır Değer Testleri: 20, 39, 40, 59, 60, 80 puanlarının seviye karşılıkları doğrulanır")
    void testCalculateStaiLevel_DefaultThresholds(int score, String expectedLevel) {
        PsychTestService service = createService(40, 60);
        String actual = service.calculateStaiLevel(score);
        assertEquals(expectedLevel, actual, "Puan " + score + " için beklenen seviye: " + expectedLevel);
    }

    @Test
    @DisplayName("Null puan verildiğinde null döner (tamamlanmamış test senaryosu)")
    void testCalculateStaiLevel_NullScore_ReturnsNull() {
        PsychTestService service = createService(40, 60);
        assertNull(service.calculateStaiLevel(null), "Null puan için seviye null dönmelidir");
    }

    @ParameterizedTest(name = "Özel eşiklerle (medium=35, high=55) puan={0} -> beklenen seviye={1}")
    @CsvSource({
            "34, LOW",
            "35, MEDIUM",
            "54, MEDIUM",
            "55, HIGH",
            "70, HIGH"
    })
    @DisplayName("Yapılandırılabilir Eşik Testi: application.properties üzerinden eşikler değiştiğinde doğru seviye üretilir")
    void testCalculateStaiLevel_CustomThresholds(int score, String expectedLevel) {
        PsychTestService service = createService(35, 55);
        String actual = service.calculateStaiLevel(score);
        assertEquals(expectedLevel, actual, "Özel eşiklerle puan " + score + " için beklenen: " + expectedLevel);
    }
}
