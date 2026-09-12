package com.hankabakc.analyzepanel.core.security;

import com.hankabakc.analyzepanel.auth.repository.BlacklistedTokenRepository;
import com.hankabakc.analyzepanel.auth.repository.RefreshTokenRepository;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * SecurityCleanupTask: Süresi dolmuş güvenlik verilerini otonom olarak temizler.
 * Sistem performansını korur ve veritabanı şişmesini engeller.
 */
@Component
public class SecurityCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SecurityCleanupTask.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final AuditLogRepository auditLogRepository;

    public SecurityCleanupTask(RefreshTokenRepository refreshTokenRepository, 
                               BlacklistedTokenRepository blacklistedTokenRepository,
                               AuditLogRepository auditLogRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * cleanExpiredTokens: Her gece saat 03:00'te süresi dolmuş tokenları temizler.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredTokens() {
        log.info("[MAINTENANCE] Güvenlik verileri temizliği başlatıldı...");
        
        // 1. Süresi dolmuş Refresh Tokenları temizle
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        
        // 2. Süresi dolmuş Kara Liste tokenlarını temizle
        blacklistedTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        
        // 3. 6 aydan eski Audit Logları temizle
        auditLogRepository.deleteByTimestampBefore(LocalDateTime.now().minusMonths(6));
        
        log.info("[MAINTENANCE] Temizlik başarıyla tamamlandı.");
    }
}
