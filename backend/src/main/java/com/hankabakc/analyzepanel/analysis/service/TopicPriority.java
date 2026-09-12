package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * TopicPriority: Öğrencinin hangi konuya önce çalışması gerektiğini karne verisinden türetir.
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.1: Sıralama bir iş kuralıdır ve yalnızca burada yaşar. İstemci sıralamayı
 *       yeniden hesaplamaz; sunucunun verdiği hazır iki listeyi (puana ve orana göre) gösterir.</li>
 *   <li>APP-01 §2.7: Kural tabanlı, ücretsiz ve deterministik. Aynı karne her zaman aynı
 *       sırayı üretir (eşitlik durumlarında ders ve konu adına göre deterministik bağlanır).</li>
 *   <li>ENG-01 §1.2 (DRY): Hem yapısal listeler hem de metin özeti bu tek kaynaktan üretilir.</li>
 *   <li>UYGULAMA_OZELLIKLERI.md §5: Ayarlanabilir parametreler (1 net puan katsayısı) koda
 *       gömülmez, dışarıdan parametre olarak alınır.</li>
 * </ul>
 */
public final class TopicPriority {

    /** Tek soruluk konular gürültü yapar; anlamlı bir eğilim için en az bu kadar soru aranır. */
    private static final int MIN_QUESTIONS = 2;

    /** Öğretmene tek ekranda gösterilebilecek makul üst sınır (her iki liste için de geçerli). */
    private static final int MAX_ITEMS = 5;

    private TopicPriority() {
    }

    /**
     * of: Ders listesinden öncelik sırasına dizilmiş iki listeyi (puana göre ve orana göre) çıkarır (T-030).
     *
     * <p>Puana Göre Sıra:
     * 1. Kaçırılan soru sayısı (lostQuestions = total - correct) / puan kaybı (azalan).
     * 2. Toplam soru sayısı (azalan).
     * 3. Başarı oranı (artan).
     * 4. Determinizm: Ders adı (artan) -> Konu adı (artan).</p>
     *
     * <p>Orana Göre Sıra:
     * 1. Başarı oranı (artan).
     * 2. Toplam soru sayısı (azalan).
     * 3. Kaçırılan soru sayısı / puan kaybı (azalan).
     * 4. Determinizm: Ders adı (artan) -> Konu adı (artan).</p>
     *
     * @param lessons Karnenin ders ve konu kırılımı (null olabilir)
     * @param puanPerNet 1 netin yaklaşık LGS puan karşılığı (örn: 4.44)
     * @param chronicTopics Son 2 denemede de WRONG olan kronik konu adları kümesi (kümülatif mod için)
     * @return Puana ve orana göre sıralanmış, her biri en fazla {@value #MAX_ITEMS} konudan oluşan listeler ikilisi (PriorityListsDto)
     */
    public static AnalysisResponse.PriorityListsDto of(
            List<AnalysisResponse.LessonDto> lessons,
            double puanPerNet,
            Set<String> chronicTopics) {
        if (lessons == null || lessons.isEmpty()) {
            return new AnalysisResponse.PriorityListsDto(List.of(), List.of());
        }

        Set<String> chronicSet = chronicTopics != null ? chronicTopics : Set.of();
        List<AnalysisResponse.PriorityTopicDto> candidates = new ArrayList<>();

        for (AnalysisResponse.LessonDto lesson : lessons) {
            if (lesson.topics() == null) continue;
            for (AnalysisResponse.TopicDto topic : lesson.topics()) {
                int total = nz(topic.totalQuestions());
                int correct = nz(topic.correctCount());
                int wrong = nz(topic.wrongCount());

                if (total < MIN_QUESTIONS || correct >= total) continue;

                int empty = Math.max(0, total - correct - wrong);
                int lostQuestions = total - correct; // Kaçırılan soru sayısı (wrong + empty)
                double rate = correct * 100.0 / total;
                double lostPointsRaw = lostQuestions * puanPerNet;
                double lostPoints = Math.round(lostPointsRaw * 10.0) / 10.0;
                boolean isChronic = chronicSet.contains(topic.topicName());

                candidates.add(new AnalysisResponse.PriorityTopicDto(
                        lesson.lessonName(),
                        topic.topicName(),
                        total,
                        correct,
                        wrong,
                        empty,
                        rate,
                        lostPoints,
                        isChronic
                ));
            }
        }

        if (candidates.isEmpty()) {
            return new AnalysisResponse.PriorityListsDto(List.of(), List.of());
        }

        // 1. Puana göre sıralama
        Comparator<AnalysisResponse.PriorityTopicDto> byPointsComparator = Comparator
                .comparingInt((AnalysisResponse.PriorityTopicDto t) -> (t.totalQuestions() - t.correctCount())).reversed()
                .thenComparing(Comparator.comparingInt(AnalysisResponse.PriorityTopicDto::totalQuestions).reversed())
                .thenComparingDouble(AnalysisResponse.PriorityTopicDto::successRate)
                .thenComparing(AnalysisResponse.PriorityTopicDto::lessonName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnalysisResponse.PriorityTopicDto::topicName, String.CASE_INSENSITIVE_ORDER);

        List<AnalysisResponse.PriorityTopicDto> byPoints = new ArrayList<>(candidates);
        byPoints.sort(byPointsComparator);

        // 2. Orana göre sıralama
        Comparator<AnalysisResponse.PriorityTopicDto> byRateComparator = Comparator
                .comparingDouble(AnalysisResponse.PriorityTopicDto::successRate)
                .thenComparing(Comparator.comparingInt(AnalysisResponse.PriorityTopicDto::totalQuestions).reversed())
                .thenComparing(Comparator.comparingInt((AnalysisResponse.PriorityTopicDto t) -> (t.totalQuestions() - t.correctCount())).reversed())
                .thenComparing(AnalysisResponse.PriorityTopicDto::lessonName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnalysisResponse.PriorityTopicDto::topicName, String.CASE_INSENSITIVE_ORDER);

        List<AnalysisResponse.PriorityTopicDto> byRate = new ArrayList<>(candidates);
        byRate.sort(byRateComparator);

        List<AnalysisResponse.PriorityTopicDto> topByPoints = byPoints.subList(0, Math.min(MAX_ITEMS, byPoints.size()));
        List<AnalysisResponse.PriorityTopicDto> topByRate = byRate.subList(0, Math.min(MAX_ITEMS, byRate.size()));

        return new AnalysisResponse.PriorityListsDto(List.copyOf(topByPoints), List.copyOf(topByRate));
    }

    /**
     * of: Tekil karne modu için (kronik geçmişi olmadan) öncelik listeleri üretir.
     */
    public static AnalysisResponse.PriorityListsDto of(
            List<AnalysisResponse.LessonDto> lessons,
            double puanPerNet) {
        return of(lessons, puanPerNet, Set.of());
    }

    /**
     * asText: Puana göre öncelik listesinin düz metin karşılığı. Veritabanında saklanan özet alanı için kullanılır.
     *
     * <p>Metin özeti tek bir sıralamaya bağlanır; en çok puan/net kazandıracak varsayılan
     * sıralamadaki (byPoints) ilk {@value #MAX_ITEMS} konuyu özetler.</p>
     */
    public static String asText(List<AnalysisResponse.PriorityTopicDto> byPoints) {
        if (byPoints == null || byPoints.isEmpty()) {
            return "Bu karnede net bir eksik konu görünmüyor; tüm konularda tam başarı sağlanmış.";
        }
        StringBuilder sb = new StringBuilder("Öncelik verilmesi gereken konular:\n");
        int count = 0;
        for (AnalysisResponse.PriorityTopicDto t : byPoints) {
            if (count++ >= MAX_ITEMS) break;
            String chronicText = t.chronic() ? " [Son 2 denemede de yanlış]" : "";
            sb.append(String.format("• %s — %s (%d soruda %d doğru, %%%.0f, ≈%.1f puan)%s%n",
                    t.lessonName(), t.topicName(), t.totalQuestions(), t.correctCount(), t.successRate(), t.lostPoints(), chronicText));
        }
        return sb.toString().trim();
    }

    /**
     * strategicPriority: Ders listesinden en zayıf dersi tespit edip tek cümlelik stratejik öncelik metni üretir (T-039).
     *
     * <p>Karnenin / kümülatif derslerin başarı yüzdeleri karşılaştırılır. Eğer veri yoksa veya
     * tüm derslerde %100 başarı sağlanmışsa null döner (uydurma cümle basılmaz - IST-02 §2, APP-01 §2.7).</p>
     *
     * @param lessons Ders analiz listesi
     * @return Stratejik öncelik metni veya null
     */
    public static String strategicPriority(List<AnalysisResponse.LessonDto> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return null;
        }

        AnalysisResponse.LessonDto weakest = null;
        double weakestRate = Double.MAX_VALUE;

        for (AnalysisResponse.LessonDto lesson : lessons) {
            if (lesson.successRate() == null) continue;
            double rate = lesson.successRate().doubleValue();
            if (rate < weakestRate) {
                weakestRate = rate;
                weakest = lesson;
            }
        }

        if (weakest == null || weakestRate >= 100.0) {
            return null;
        }

        return String.format(java.util.Locale.ROOT, "%s dersine öncelik verilmeli (%%%d başarı).", weakest.lessonName(), Math.round(weakestRate));
    }

    /**
     * teacherActionPlan: Kronik konular veya en çok puan kaybettiren konular üzerinden öğretmen aksiyon planı üretir (T-039).
     *
     * <p>Öncelik sıralaması:
     * 1. Kronik konular varsa (Son 2 denemede de yanlış yapılmış konular): Bu konular öne çıkarılır.
     * 2. Kronik konu yok ama puan kaybettiren konular varsa: En çok puan kaybettiren ilk 1-3 konu özetlenir.
     * 3. Hiç eksik konu yoksa: null döner ve arayüzde kart gizlenir.</p>
     *
     * @param chronicTopics Son 2 denemede de yanlış yapılan kronik konu adları
     * @param byPoints Puana göre öncelikli konu listesi
     * @return Öğretmen aksiyon planı metni veya null
     */
    public static String teacherActionPlan(List<String> chronicTopics, List<AnalysisResponse.PriorityTopicDto> byPoints) {
        if (chronicTopics != null && !chronicTopics.isEmpty()) {
            int limit = Math.min(3, chronicTopics.size());
            String topicsStr = String.join(", ", chronicTopics.subList(0, limit));
            return String.format(
                    "Kronik sorunlu konular: %s.%nBu konularda temel kavram tekrarları ve hedefe yönelik soru çalışmaları yapılmalıdır.",
                    topicsStr
            );
        }

        if (byPoints != null && !byPoints.isEmpty()) {
            int limit = Math.min(3, byPoints.size());
            String topicsStr = byPoints.stream()
                    .limit(limit)
                    .map(AnalysisResponse.PriorityTopicDto::topicName)
                    .collect(java.util.stream.Collectors.joining(", "));
            return String.format(
                    "En çok puan kaybettiren konular: %s.%nBu başlıklarda soru çözümü ve pekiştirme çalışmaları önerilir.",
                    topicsStr
            );
        }

        return null;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
