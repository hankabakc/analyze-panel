package com.hankabakc.analyzepanel.auth.repository;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RefreshTokenRepository: RefreshToken veritabanı erişim arayüzü.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUser(AppUser user);

    /**
     * Yalnızca henüz rotasyona uğramamış (aktif) oturumları listeler.
     * 3 eşzamanlı oturum kotası denetiminde kullanılır (T-006 / K1).
     */
    List<RefreshToken> findAllByUserAndReplacedAtIsNullOrderByExpiryDateAsc(AppUser user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByExpiryDateBefore(Instant now);

    /**
     * Kullanıcının tüm refresh token satırlarını siler (Token Family Revocation / Oturum Kapatma).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteByUser(AppUser user);
}
