package com.hankabakc.analyzepanel.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.core.security.PiiConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * AppUser: Sistemdeki tüm kullanıcıların (Öğrenci, Öğretmen, Yönetici) temel varlık nesnesidir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K1, K2: E-posta + şifre ile kimlik doğrulama desteği eklenmiştir. Şifre BCrypt cost 12
 *       ile hash'lenir (password sütununda PiiConverter kullanılmaz, hash geri döndürülemez).</li>
 *   <li>T-009 / K5: İlk girişte veya yönetici tarafından atanan şifrelerde mustChangePassword bayrağı ile
 *       zorunlu şifre değişikliği denetlenir.</li>
 *   <li>ENG-13 §1: Kişisel veriler (PII: email, fullName) veritabanında AES-256 (PiiConverter)
 *       ile şifrelenmiş olarak saklanır. Base64 çıktısını taşımak için sütun uzunlukları 500 karakterdir.</li>
 *   <li>T-008B / K2: Otomatik ID üretimi (@GeneratedValue) tek standart olarak benimsenmiştir.</li>
 *   <li>T-016 / S-010: Öğrenciler için sınıf seviyesi (grade: 5-12) zorunludur, öğretmen ve yöneticilerde null tutulur.</li>
 * </ul>
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, unique = true, length = 500)
    private String email;

    @Convert(converter = PiiConverter.class)
    @Column(name = "full_name", length = 500)
    private String fullName;

    @JsonIgnore
    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "grade")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    private Integer grade;

    /**
     * T-040: Öğrencinin adlandırılmış sınıfı (örn. "12-A"). Öğretmen/yönetici için null.
     *
     * <p>{@code grade} ile karıştırılmamalı: grade "kaçıncı sınıf" seviyesidir (5-12),
     * bu alan ise gruptur. Sınıf silinirse bu alan NULL olur, öğrenci silinmez.</p>
     */
    @Column(name = "class_id")
    private java.util.UUID classId;

    /** T-037: Son başarılı giriş zamanı. Hiç giriş yapılmadıysa null kalır. */
    @Column(name = "last_login_at")
    private java.time.Instant lastLoginAt;

    @Column(name = "credentials_invalidated_at")
    private java.time.Instant credentialsInvalidatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public AppUser() {
    }

    public AppUser(String email, String fullName, UserRole role, UserStatus status) {
        this(email, fullName, role, status, null, null, false);
    }

    public AppUser(String email, String fullName, UserRole role, UserStatus status, Integer grade) {
        this(email, fullName, role, status, grade, null, false);
    }

    public AppUser(String email, String fullName, UserRole role, UserStatus status, Integer grade, String password, boolean mustChangePassword) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.grade = grade;
        this.password = password;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public java.util.UUID getClassId() { return classId; }
    public void setClassId(java.util.UUID classId) { this.classId = classId; }
    public java.time.Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public java.time.Instant getCredentialsInvalidatedAt() { return credentialsInvalidatedAt; }
    public void setCredentialsInvalidatedAt(java.time.Instant credentialsInvalidatedAt) { this.credentialsInvalidatedAt = credentialsInvalidatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppUser appUser = (AppUser) o;
        return Objects.equals(id, appUser.id) && Objects.equals(email, appUser.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "id=" + id +
                ", email='" + maskEmail(email) + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role=" + role +
                ", status=" + status +
                ", grade=" + grade +
                ", mustChangePassword=" + mustChangePassword +
                '}';
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        return email.replaceAll("(^[^@]{3}|(?!^)\\G)[^@]", "$1*");
    }
}
