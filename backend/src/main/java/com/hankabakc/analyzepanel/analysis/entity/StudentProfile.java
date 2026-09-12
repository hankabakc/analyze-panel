package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * StudentProfile: Öğrencinin hedef okul, hedef puan ve mevcut seviye gibi 
 * akademik profil bilgilerini saklar.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "target_school_id")
    private UUID targetSchoolId;

    @Column(name = "target_score")
    private BigDecimal targetScore;

    @Column(name = "current_level")
    private String currentLevel; // BEGINNER, INTERMEDIATE, ADVANCED

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    public StudentProfile() {}

    public StudentProfile(UUID id, UUID userId) {
        this.id = id;
        this.userId = userId;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /* Getter ve Setter Metotları */
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getTargetSchoolId() { return targetSchoolId; }
    public void setTargetSchoolId(UUID targetSchoolId) { this.targetSchoolId = targetSchoolId; }

    public BigDecimal getTargetScore() { return targetScore; }
    public void setTargetScore(BigDecimal targetScore) { this.targetScore = targetScore; }

    public String getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
