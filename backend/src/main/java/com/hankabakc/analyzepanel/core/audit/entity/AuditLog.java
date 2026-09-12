package com.hankabakc.analyzepanel.core.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog: Sistemsel işlemlerin denetim günlüğüdür.
 * OWASP 2026: Logging and Monitoring (A09) kapsamında izlenebilirlik sağlar.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String action; // Yapılan işlem (Örn: LOGIN, REPORT_DELETE)

    @Column(name = "user_id")
    private UUID userId; // T-042: İşlemi yapan kullanıcının UUID kimliği

    @Column(name = "user_email")
    private String userEmail; // Yalnızca ANONYMOUS veya "user" gibi yer tutucular; gerçek e-posta PII olduğundan yazılmaz (T-042)

    @Column(nullable = false)
    private String ipAddress; // İşlemin yapıldığı IP

    @Column(length = 1000)
    private String details; // İşlem detayları (Örn: Rapor ID, Öğrenci ID)

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AuditLog() {}

    /**
     * T-042: Birincil denetim logu kurucusu.
     * Kullanıcı kimliği UUID olarak tutulur; gerçek e-posta PII koruması nedeniyle yazılmaz.
     */
    public AuditLog(String action, UUID userId, String userEmail, String ipAddress, String details) {
        this.action = action;
        this.userId = userId;
        this.userEmail = userEmail;
        this.ipAddress = ipAddress;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Geriye dönük uyumluluk kurucusu: userId olmadan yer tutucu e-posta ile kayıt oluşturur.
     */
    public AuditLog(String action, String userEmail, String ipAddress, String details) {
        this(action, null, userEmail, ipAddress, details);
    }

    // Getters
    public UUID getId() { return id; }
    public String getAction() { return action; }
    public UUID getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public String getIpAddress() { return ipAddress; }
    public String getDetails() { return details; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
