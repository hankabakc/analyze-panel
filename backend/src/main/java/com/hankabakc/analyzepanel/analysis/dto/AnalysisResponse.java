package com.hankabakc.analyzepanel.analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AnalysisResponse: 5'li Birleştirilmiş Karne Analizi sonucunu taşıyan ana DTO.
 */
public record AnalysisResponse(
    UUID id,
    String fileName,
    String examTitle,
    LocalDateTime processedAt,
    String status,
    Integer intendedExamCount,
    String validationErrors,
    String reportType, // SINGLE, SUMMARY
    String mentorFeedback, // Koçluk Notları
    String futureProjection, // Gelecek Tahmini
    String strategicPriority, // Kritik Öncelik
    String teacherActionPlan, // Yeni: Öğretmen Aksiyon Planı
    String targetSchoolName, // Yeni: Hedef Okul
    java.math.BigDecimal targetSchoolScore, // Yeni: Hedef Puan
    
    // Sınavların Listesi
    List<ExamSummaryDto> examList,
    
    // Birleştirilmiş Genel Sonuç (Ders ve Konu Bazlı)
    ConsolidatedResultDto consolidatedResult,
    
    String globalFeedback,

    // Kümülatif Trend Analizi Verileri
    TopicTrendDataDto topicTrendData,

    /**
     * Öncelik sırasına dizilmiş eksik konu listeleri (T-030).
     * İki liste de (puana göre ve orana göre) sunucuda sıralanıp ilk 5 ile sınırlandırılır;
     * istemci yalnızca gösterir (APP-01 §2.1). globalFeedback puana göre listenin düz metin hâlidir.
     */
    PriorityListsDto priorityLists,

    /**
     * Hedef okul ile mevcut performansın LGS ölçeğinde karşılaştırması (T-028).
     * Hedef okul seçili değilse null döner.
     */
    TargetComparisonDto targetComparison
) {

    /**
     * PriorityListsDto: Puana ve orana göre sunucuda sıralanmış ilk 5 öncelikli konu listeleri (T-030).
     */
    public record PriorityListsDto(
        List<PriorityTopicDto> byPoints,
        List<PriorityTopicDto> byRate
    ) {}

    /**
     * TargetComparisonDto: Hedeflenen okul ile performansın LGS ölçeğinde karşılaştırması (T-028).
     */
    public record TargetComparisonDto(
        String schoolName,
        BigDecimal targetScore,
        double averageNet,
        double predictedLgsScore,
        int compatibilityPercent,
        double scoreGap,
        double netGap
    ) {}

    /**
     * PriorityTopicDto: Öğrencinin öncelikle çalışması gereken bir konu ve kanıtı.
     * Yüzde tek başına yanıltıcıdır; soru sayısı da taşınır ki 2 soruluk bir konu
     * 10 soruluk bir konuyla aynı ağırlıkta görünmesin.
     */
    public record PriorityTopicDto(
        String lessonName,
        String topicName,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int emptyCount,
        double successRate,
        double lostPoints,
        boolean chronic
    ) {}
    public record TopicTrendDataDto(
        List<TopicHistoryDto> heatmap,
        List<String> chronicTopics,
        List<String> improvedTopics,
        List<String> inconsistentTopics
    ) {}

    public record TopicHistoryDto(
        String lessonName,
        String topicName,
        List<String> statusHistory // CORRECT, WRONG, EMPTY listesi
    ) {}

    /**
     * ExamSummaryDto: Karnenin üst kısmındaki 5 sınavın özeti.
     */
    public record ExamSummaryDto(
        String examName,
        String examDate,
        BigDecimal totalScore
    ) {}

    /**
     * ConsolidatedResultDto: 5 denemenin toplam ders ve konu başarıları.
     */
    public record ConsolidatedResultDto(
        List<LessonDto> lessons
    ) {}

    public record LessonDto(
        UUID id,
        String lessonName,
        Integer correct,
        Integer wrong,
        Integer empty,
        BigDecimal successRate,
        List<TopicDto> topics
    ) {}

    /**
     * TopicDto: Bir konuya ait karneden türetilmiş başarı ve performans bandı bilgisi (T-030C).
     *
     * @param performanceBand "STRONG" (>= %85), "MEDIUM" (>= %50), "WEAK" (< %50)
     */
    public record TopicDto(
        UUID id,
        String topicName,
        String status,
        String aiSuggestion,
        Integer totalQuestions,
        Integer correctCount,
        Integer wrongCount,
        String performanceBand
    ) {
        public TopicDto(UUID id, String topicName, String status, String aiSuggestion, Integer totalQuestions, Integer correctCount, Integer wrongCount) {
            this(id, topicName, status, aiSuggestion, totalQuestions, correctCount, wrongCount,
                 (totalQuestions != null && totalQuestions > 0 && correctCount != null)
                     ? ((correctCount * 100.0 / totalQuestions >= 85.0) ? "STRONG" : (correctCount * 100.0 / totalQuestions >= 50.0) ? "MEDIUM" : "WEAK")
                     : "WEAK");
        }
    }
}
