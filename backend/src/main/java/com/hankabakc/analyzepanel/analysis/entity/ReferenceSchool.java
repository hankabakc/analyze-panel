package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * ReferenceSchool: 2025 LGS verilerine dayalı referans okul bilgileridir.
 */
@Entity
@Table(name = "reference_schools")
public class ReferenceSchool {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String city;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "school_type")
    private String schoolType;

    @Column(name = "base_score")
    private BigDecimal baseScore;

    @Column(name = "percentile")
    private BigDecimal percentile;

    public ReferenceSchool() {}

    public ReferenceSchool(UUID id, String city, String schoolName, String schoolType, BigDecimal baseScore, BigDecimal percentile) {
        this.id = id;
        this.city = city;
        this.schoolName = schoolName;
        this.schoolType = schoolType;
        this.baseScore = baseScore;
        this.percentile = percentile;
    }

    /* Getter ve Setter Metotları */
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getSchoolType() { return schoolType; }
    public void setSchoolType(String schoolType) { this.schoolType = schoolType; }

    public BigDecimal getBaseScore() { return baseScore; }
    public void setBaseScore(BigDecimal baseScore) { this.baseScore = baseScore; }

    public BigDecimal getPercentile() { return percentile; }
    public void setPercentile(BigDecimal percentile) { this.percentile = percentile; }
}
