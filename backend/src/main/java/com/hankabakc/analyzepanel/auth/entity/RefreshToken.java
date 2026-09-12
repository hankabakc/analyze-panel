package com.hankabakc.analyzepanel.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * RefreshToken: Kullanıcıların uzun süreli oturumlarını yönetmek için kullanılır.
 * T-006 / ENG-11 §2.3: Refresh token rotasyonu ve yeniden kullanım tespiti desteklenir.
 * T-007 / K4: "Beni hatırla" (30 gün) seçimi remembered alanında saklanır ve rotasyonda devredilir.
 * 
 * <p>İlkeler:</p>
 * <ul>
 *   <li>1. Rotasyon (K1): Yenilemede eski token silinmez; replacedAt zaman damgası vurulur.</li>
 *   <li>2. Yeniden Kullanım Tespiti (K2): replacedAt dolu bir token tekrar sunulursa kullanıcının
 *       tüm token ailesi veritabanından silinir (Token Family Revocation).</li>
 *   <li>3. Sabit Tavan (K3): Rotasyonda expiryDate uzatılmaz, eski satırdan devralınır.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private AppUser user;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(nullable = false)
    private boolean remembered = false;

    public RefreshToken() {}

    public RefreshToken(String token, AppUser user, Instant expiryDate) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.remembered = false;
    }

    public RefreshToken(String token, AppUser user, Instant expiryDate, boolean remembered) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.remembered = remembered;
    }

    public RefreshToken(String token, AppUser user, Instant expiryDate, Instant replacedAt, boolean remembered) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.replacedAt = replacedAt;
        this.remembered = remembered;
    }

    public UUID getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }
    public Instant getReplacedAt() { return replacedAt; }
    public void setReplacedAt(Instant replacedAt) { this.replacedAt = replacedAt; }
    public boolean isRemembered() { return remembered; }
    public void setRemembered(boolean remembered) { this.remembered = remembered; }
}
