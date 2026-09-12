package com.hankabakc.analyzepanel.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PerformanceBandCalculator: Konuların başarı oranına göre performans bandını (STRONG, MEDIUM, WEAK) belirler (T-030C).
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.1: Başarı eşikleri ve bant kararları sunucuda çalışır; istemci sayısal eşik bilmez.</li>
 *   <li>ENG-01 §1.2 (DRY): Eşikler tek bir merkezde yönetilir.</li>
 *   <li>UYGULAMA_OZELLIKLERI.md §5: Eşik değerleri koda gömülmez, application.properties üzerinden okunur.</li>
 * </ul>
 */
@Component
public class PerformanceBandCalculator {

    private final double thresholdStrong;
    private final double thresholdMedium;

    public PerformanceBandCalculator(
            @Value("${analysis.topic.threshold-strong:85.0}") double thresholdStrong,
            @Value("${analysis.topic.threshold-medium:50.0}") double thresholdMedium) {
        this.thresholdStrong = thresholdStrong;
        this.thresholdMedium = thresholdMedium;
    }

    /**
     * calculateBand: Soru sayısı ve doğru sayısına göre performans bandını hesaplar.
     *
     * @param totalQuestions Konudaki toplam soru sayısı (null olabilir)
     * @param correctCount Konudaki doğru sayısı (null olabilir)
     * @return "STRONG" (>= thresholdStrong), "MEDIUM" (>= thresholdMedium) veya "WEAK" (< thresholdMedium)
     */
    public String calculateBand(Integer totalQuestions, Integer correctCount) {
        int total = totalQuestions != null ? totalQuestions : 0;
        int correct = correctCount != null ? correctCount : 0;
        if (total <= 0) {
            return "WEAK";
        }
        double percentage = (correct * 100.0) / total;
        if (percentage >= thresholdStrong) {
            return "STRONG";
        }
        if (percentage >= thresholdMedium) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    public double getThresholdStrong() {
        return thresholdStrong;
    }

    public double getThresholdMedium() {
        return thresholdMedium;
    }
}
