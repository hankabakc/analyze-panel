package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * TopicDetail: Bir dersin altındaki spesifik bir konuya ait analiz verisidir.
 * AI tarafından oluşturulan "destek önerisi" bu tabloda saklanır.
 */
@Entity
@Table(name = "topic_details")
public class TopicDetail {

    @Id
    private UUID id;

    @Column(name = "lesson_analysis_id", nullable = false)
    private UUID lessonAnalysisId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "status", length = 20)
    private String status; // CORRECT, WRONG, EMPTY

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    /**
     * Konu için öneri metni.
     *
     * <p><b>İsim uyarısı (T-039A):</b> Alan adındaki "ai" eki tarihseldir; bu değer bir dış AI
     * servisinden gelmez (APP-01 §1.1, §2.7). Bugün ayrıştırıcı bu alanı doldurmaz ({@code null}
     * geçilir); öneriler kural tabanlı olarak {@code TopicPriority} tarafından üretilir.
     * Sütun adı, mevcut şema ve kayıtlarla uyum bozulmasın diye değiştirilmedi.</p>
     */
    private String aiSuggestion;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "wrong_count")
    private Integer wrongCount;

    public TopicDetail() {
    }

    public TopicDetail(UUID id, UUID lessonAnalysisId, String topicName, String status, String aiSuggestion) {
        this.id = id;
        this.lessonAnalysisId = lessonAnalysisId;
        this.topicName = topicName;
        this.status = status;
        this.aiSuggestion = aiSuggestion;
        this.totalQuestions = 0;
        this.correctCount = 0;
        this.wrongCount = 0;
    }

    public TopicDetail(UUID id, UUID lessonAnalysisId, String topicName, String status, String aiSuggestion, Integer totalQuestions, Integer correctCount, Integer wrongCount) {
        this.id = id;
        this.lessonAnalysisId = lessonAnalysisId;
        this.topicName = topicName;
        this.status = status;
        this.aiSuggestion = aiSuggestion;
        this.totalQuestions = totalQuestions;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
    }

    /* Getter ve Setter Metotları */
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getLessonAnalysisId() { return lessonAnalysisId; }
    public void setLessonAnalysisId(UUID lessonAnalysisId) { this.lessonAnalysisId = lessonAnalysisId; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAiSuggestion() { return aiSuggestion; }
    public void setAiSuggestion(String aiSuggestion) { this.aiSuggestion = aiSuggestion; }
}
