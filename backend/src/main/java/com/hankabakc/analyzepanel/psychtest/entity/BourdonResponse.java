package com.hankabakc.analyzepanel.psychtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * BourdonResponse: Bourdon dikkat testi tamamlama kaydını ve blok bazlı puanlarını tutan JPA varlığı.
 * Pure Java Politikası: Lombok kullanılmamıştır.
 */
@Entity
@Table(name = "bourdon_responses")
public class BourdonResponse {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false, unique = true)
    private UUID assignmentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "marked_cells", nullable = false, columnDefinition = "TEXT")
    private String markedCells;

    @Column(name = "b1_correct", nullable = false)
    private int b1Correct;

    @Column(name = "b1_omitted", nullable = false)
    private int b1Omitted;

    @Column(name = "b1_incorrect", nullable = false)
    private int b1Incorrect;

    @Column(name = "b2_correct", nullable = false)
    private int b2Correct;

    @Column(name = "b2_omitted", nullable = false)
    private int b2Omitted;

    @Column(name = "b2_incorrect", nullable = false)
    private int b2Incorrect;

    @Column(name = "b3_correct", nullable = false)
    private int b3Correct;

    @Column(name = "b3_omitted", nullable = false)
    private int b3Omitted;

    @Column(name = "b3_incorrect", nullable = false)
    private int b3Incorrect;

    @Column(name = "total_correct", nullable = false)
    private int totalCorrect;

    @Column(name = "total_omitted", nullable = false)
    private int totalOmitted;

    @Column(name = "total_incorrect", nullable = false)
    private int totalIncorrect;

    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BourdonResponse() {
    }

    public BourdonResponse(UUID id, UUID assignmentId, Instant startedAt, Instant submittedAt,
                           int durationSeconds, boolean timedOut, String markedCells,
                           int b1Correct, int b1Omitted, int b1Incorrect,
                           int b2Correct, int b2Omitted, int b2Incorrect,
                           int b3Correct, int b3Omitted, int b3Incorrect,
                           int totalCorrect, int totalOmitted, int totalIncorrect,
                           Instant createdAt) {
        this.id = id;
        this.assignmentId = assignmentId;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
        this.durationSeconds = durationSeconds;
        this.timedOut = timedOut;
        this.markedCells = markedCells;
        this.b1Correct = b1Correct;
        this.b1Omitted = b1Omitted;
        this.b1Incorrect = b1Incorrect;
        this.b2Correct = b2Correct;
        this.b2Omitted = b2Omitted;
        this.b2Incorrect = b2Incorrect;
        this.b3Correct = b3Correct;
        this.b3Omitted = b3Omitted;
        this.b3Incorrect = b3Incorrect;
        this.totalCorrect = totalCorrect;
        this.totalOmitted = totalOmitted;
        this.totalIncorrect = totalIncorrect;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public BourdonResponse(UUID id, UUID assignmentId, Instant startedAt, Instant submittedAt,
                           int durationSeconds, String markedCells,
                           int b1Correct, int b1Omitted, int b1Incorrect,
                           int b2Correct, int b2Omitted, int b2Incorrect,
                           int b3Correct, int b3Omitted, int b3Incorrect,
                           int totalCorrect, int totalOmitted, int totalIncorrect,
                           Instant createdAt) {
        this(id, assignmentId, startedAt, submittedAt, durationSeconds, false, markedCells,
                b1Correct, b1Omitted, b1Incorrect,
                b2Correct, b2Omitted, b2Incorrect,
                b3Correct, b3Omitted, b3Incorrect,
                totalCorrect, totalOmitted, totalIncorrect, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(UUID assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    public String getMarkedCells() {
        return markedCells;
    }

    public void setMarkedCells(String markedCells) {
        this.markedCells = markedCells;
    }

    public int getB1Correct() {
        return b1Correct;
    }

    public void setB1Correct(int b1Correct) {
        this.b1Correct = b1Correct;
    }

    public int getB1Omitted() {
        return b1Omitted;
    }

    public void setB1Omitted(int b1Omitted) {
        this.b1Omitted = b1Omitted;
    }

    public int getB1Incorrect() {
        return b1Incorrect;
    }

    public void setB1Incorrect(int b1Incorrect) {
        this.b1Incorrect = b1Incorrect;
    }

    public int getB2Correct() {
        return b2Correct;
    }

    public void setB2Correct(int b2Correct) {
        this.b2Correct = b2Correct;
    }

    public int getB2Omitted() {
        return b2Omitted;
    }

    public void setB2Omitted(int b2Omitted) {
        this.b2Omitted = b2Omitted;
    }

    public int getB2Incorrect() {
        return b2Incorrect;
    }

    public void setB2Incorrect(int b2Incorrect) {
        this.b2Incorrect = b2Incorrect;
    }

    public int getB3Correct() {
        return b3Correct;
    }

    public void setB3Correct(int b3Correct) {
        this.b3Correct = b3Correct;
    }

    public int getB3Omitted() {
        return b3Omitted;
    }

    public void setB3Omitted(int b3Omitted) {
        this.b3Omitted = b3Omitted;
    }

    public int getB3Incorrect() {
        return b3Incorrect;
    }

    public void setB3Incorrect(int b3Incorrect) {
        this.b3Incorrect = b3Incorrect;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public void setTotalCorrect(int totalCorrect) {
        this.totalCorrect = totalCorrect;
    }

    public int getTotalOmitted() {
        return totalOmitted;
    }

    public void setTotalOmitted(int totalOmitted) {
        this.totalOmitted = totalOmitted;
    }

    public int getTotalIncorrect() {
        return totalIncorrect;
    }

    public void setTotalIncorrect(int totalIncorrect) {
        this.totalIncorrect = totalIncorrect;
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
        BourdonResponse that = (BourdonResponse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
