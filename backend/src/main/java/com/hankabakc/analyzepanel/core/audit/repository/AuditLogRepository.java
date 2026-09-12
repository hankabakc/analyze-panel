package com.hankabakc.analyzepanel.core.audit.repository;

import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Modifying
    void deleteByTimestampBefore(LocalDateTime timestamp);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    void deleteByUserEmailIn(java.util.Collection<String> userEmails);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    void deleteByUserIdIn(java.util.Collection<UUID> userIds);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM AuditLog a WHERE a.userEmail = :userEmail AND a.timestamp >= :timestamp")
    void deleteByUserEmailAndTimestampGreaterThanEqual(
            @org.springframework.data.repository.query.Param("userEmail") String userEmail,
            @org.springframework.data.repository.query.Param("timestamp") LocalDateTime timestamp
    );

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :timestamp")
    void deleteByUserIdAndTimestampGreaterThanEqual(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("timestamp") LocalDateTime timestamp
    );
}
