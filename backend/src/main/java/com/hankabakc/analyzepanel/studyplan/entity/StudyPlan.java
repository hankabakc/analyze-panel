package com.hankabakc.analyzepanel.studyplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * StudyPlan: Öğretmenin belirli bir öğrenciye atadığı çalışma planı varlığıdır.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>Pure Java Politikası: Lombok kullanılmaz; tüm getter, setter ve yapıcı metotlar manuel yazılmıştır.</li>
 *   <li>T-050A: study_plans tablosu ile eşleşir (V35 şeması).</li>
 *   <li>ENG-11 §3.1: student_id ve teacher_id üzerinden IDOR ve erişim yetkisi doğrulanır.</li>
 * </ul>
 */
@Entity
@Table(name = "study_plans")
public class StudyPlan {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StudyPlan() {
    }

    public StudyPlan(UUID id, UUID studentId, UUID teacherId, LocalDate dueDate, Instant createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.dueDate = dueDate;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /* Getter ve Setter Metotları */

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(UUID teacherId) {
        this.teacherId = teacherId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudyPlan studyPlan = (StudyPlan) o;
        return Objects.equals(id, studyPlan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
