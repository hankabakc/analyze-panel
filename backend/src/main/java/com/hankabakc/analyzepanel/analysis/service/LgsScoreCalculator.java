package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * LgsScoreCalculator: Netten LGS puanı projeksiyonu ve hedef okul karşılaştırması yapar (T-028).
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.1: İş kuralları ve ölçek dönüşümleri sunucuda yapılır; istemci aritmetik hesaplamaz.</li>
 *   <li>ENG-01 §1.2 (DRY): Taban puan ve 1 net puan katsayısı tek bir merkezde yönetilir.</li>
 *   <li>IST-02 §2: Doğru ve dürüst bilgi sunulur; net ile LGS puanı doğrudan karşılaştırılamaz.</li>
 *   <li>UYGULAMA_OZELLIKLERI.md §5: Ayarlanabilir katsayılar koda gömülmez, yapılandırmadan okunur.</li>
 * </ul>
 */
@Component
public class LgsScoreCalculator {

    private final double puanPerNet;
    private final double tabanPuan;

    public LgsScoreCalculator(
            @Value("${analysis.lgs.puan-per-net:4.44}") double puanPerNet,
            @Value("${analysis.lgs.taban-puan:100.0}") double tabanPuan) {
        // T-028B: netGap hesabı puanPerNet'e böler. Sıfır veya negatif katsayı yapılandırma
        // hatasıdır; sessizce Infinity üretip istemciye geçersiz JSON göndermek yerine
        // uygulama açılışta durur (ENG-03 §2).
        if (puanPerNet <= 0) {
            throw new IllegalArgumentException(
                    "analysis.lgs.puan-per-net sıfırdan büyük olmalıdır, verilen: " + puanPerNet);
        }
        this.puanPerNet = puanPerNet;
        this.tabanPuan = tabanPuan;
    }

    public double getPuanPerNet() {
        return puanPerNet;
    }

    public double getTabanPuan() {
        return tabanPuan;
    }

    /**
     * calculatePredictedScore: Öğrencinin ortalama netini yaklaşık LGS puanına çevirir (T-028).
     *
     * <p>Formül: Taban Puan (100) + (Net × Puan/Net)</p>
     *
     * @param averageNet Öğrencinin denemelerdeki net ortalaması
     * @return 1 ondalık basamağa yuvarlanmış tahmini LGS puanı
     */
    public double calculatePredictedScore(double averageNet) {
        double raw = tabanPuan + (averageNet * puanPerNet);
        return Math.round(raw * 10.0) / 10.0;
    }

    /**
     * compareWithTarget: Hedeflenen okul ile öğrencinin performansını karşılaştırır (T-028).
     *
     * @param schoolName Hedef okulun adı
     * @param targetScore Hedef okulun taban puanı
     * @param averageNet Öğrencinin ortalama neti
     * @return Hedef okul karşılaştırma detay DTO'su (hedef okul yoksa null)
     */
    public AnalysisResponse.TargetComparisonDto compareWithTarget(
            String schoolName, BigDecimal targetScore, double averageNet) {
        if (schoolName == null || targetScore == null || targetScore.doubleValue() <= 0) {
            return null;
        }

        double target = targetScore.doubleValue();
        double predicted = calculatePredictedScore(averageNet);
        int compatibility = (int) Math.min(100, Math.max(0, Math.round((predicted / target) * 100.0)));
        double scoreGap = Math.round((target - predicted) * 10.0) / 10.0;
        double netGap = Math.round((scoreGap / puanPerNet) * 10.0) / 10.0;

        return new AnalysisResponse.TargetComparisonDto(
                schoolName,
                targetScore,
                Math.round(averageNet * 100.0) / 100.0,
                predicted,
                compatibility,
                scoreGap,
                netGap
        );
    }
}
