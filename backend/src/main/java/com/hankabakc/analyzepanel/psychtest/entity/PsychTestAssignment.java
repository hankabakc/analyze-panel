package com.hankabakc.analyzepanel.psychtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PsychTestAssignment: Bir öğrenciye atanan psikolojik ölçek görevini temsil eden JPA varlığı.
 * Pure Java Politikası: Lombok kullanılmamıştır.
 */
@Entity
@Table(name = "psych_test_assignments")
public class PsychTestAssignment {

    @Id
    private UUID id;

    @Column(name = "test_code", nullable = false, length = 50)
    private String testCode;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PsychTestStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "state_score")
    private Integer stateScore;

    @Column(name = "trait_score")
    private Integer traitScore;

    public PsychTestAssignment() {
    }

    public PsychTestAssignment(UUID id, String testCode, UUID studentId, UUID assignedBy, Instant assignedAt, PsychTestStatus status) {
        this.id = id;
        this.testCode = testCode;
        this.studentId = studentId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.status = status;
    }

    public PsychTestAssignment(UUID id, String testCode, UUID studentId, UUID assignedBy, Instant assignedAt,
                               PsychTestStatus status, Instant completedAt, Integer stateScore, Integer traitScore) {
        this.id = id;
        this.testCode = testCode;
        this.studentId = studentId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.status = status;
        this.completedAt = completedAt;
        this.stateScore = stateScore;
        this.traitScore = traitScore;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public PsychTestStatus getStatus() {
        return status;
    }

    public void setStatus(PsychTestStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getStateScore() {
        return stateScore;
    }

    public void setStateScore(Integer stateScore) {
        this.stateScore = stateScore;
    }

    public Integer getTraitScore() {
        return traitScore;
    }

    public void setTraitScore(Integer traitScore) {
        this.traitScore = traitScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PsychTestAssignment that = (PsychTestAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
