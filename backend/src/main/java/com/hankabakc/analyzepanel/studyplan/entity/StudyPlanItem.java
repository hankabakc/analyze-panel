package com.hankabakc.analyzepanel.studyplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * StudyPlanItem: Bir çalışma planı içerisindeki tekil konu/hedef kalemidir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>Pure Java Politikası: Lombok kullanılmaz; tüm getter, setter ve yapıcı metotlar manuel yazılmıştır.</li>
 *   <li>T-050A & T-050D: study_plan_items tablosu ile eşleşir (V35 & V36 şeması).</li>
 *   <li>completed_at: Öğrenci tamamladığında sunucu zamanıyla damgalanır; başlangıçta null'dır.</li>
 *   <li>teacher_seen_at: Öğretmen tamamlanan görevi görüntülediğinde sunucu zamanıyla damgalanır; başlangıçta null'dır (T-050D).</li>
 * </ul>
 */
@Entity
@Table(name = "study_plan_items")
public class StudyPlanItem {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "teacher_seen_at")
    private Instant teacherSeenAt;

    public StudyPlanItem() {
    }

    public StudyPlanItem(UUID id, UUID planId, String topicName, Integer questionCount, Instant completedAt) {
        this(id, planId, topicName, questionCount, completedAt, null);
    }

    public StudyPlanItem(UUID id, UUID planId, String topicName, Integer questionCount, Instant completedAt, Instant teacherSeenAt) {
        this.id = id;
        this.planId = planId;
        this.topicName = topicName;
        this.questionCount = questionCount;
        this.completedAt = completedAt;
        this.teacherSeenAt = teacherSeenAt;
    }

    /* Getter ve Setter Metotları */

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getTeacherSeenAt() {
        return teacherSeenAt;
    }

    public void setTeacherSeenAt(Instant teacherSeenAt) {
        this.teacherSeenAt = teacherSeenAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudyPlanItem that = (StudyPlanItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
