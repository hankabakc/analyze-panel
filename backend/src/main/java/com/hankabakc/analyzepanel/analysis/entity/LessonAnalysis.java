package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * LessonAnalysis: Bir rapora ait ders bazlı başarı özetidir.
 * Örn: Matematik dersinden kaç doğru, kaç yanlış yapıldığı bilgisini tutar.
 */
@Entity
@Table(name = "lesson_analyses")
public class LessonAnalysis {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "lesson_name", nullable = false, length = 100)
    private String lessonName;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "wrong_count")
    private Integer wrongCount;

    @Column(name = "empty_count")
    private Integer emptyCount;

    @Column(name = "success_rate")
    private BigDecimal successRate;

    public LessonAnalysis() {
    }

    public LessonAnalysis(UUID id, UUID reportId, String lessonName, Integer correctCount, Integer wrongCount, Integer emptyCount, BigDecimal successRate) {
        this.id = id;
        this.reportId = reportId;
        this.lessonName = lessonName;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.emptyCount = emptyCount;
        this.successRate = successRate;
    }

    /* Getter ve Setter Metotları */
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }

    public String getLessonName() { return lessonName; }
    public void setLessonName(String lessonName) { this.lessonName = lessonName; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }

    public Integer getEmptyCount() { return emptyCount; }
    public void setEmptyCount(Integer emptyCount) { this.emptyCount = emptyCount; }

    public BigDecimal getSuccessRate() { return successRate; }
    public void setSuccessRate(BigDecimal successRate) { this.successRate = successRate; }
}
