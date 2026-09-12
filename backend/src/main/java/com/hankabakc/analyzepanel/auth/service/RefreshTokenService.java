package com.hankabakc.analyzepanel.auth.service;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.RefreshToken;
import com.hankabakc.analyzepanel.auth.repository.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RefreshTokenService: Uzun ömürlü oturum token'larının yaşam döngüsünü, rotasyonunu,
 * yeniden kullanım tespitini ve "beni hatırla" sürelerini yönetir (T-006 / T-007 / ENG-11 §2.3, §2.4).
 * 
 * <p>Güvenlik İlkeleri:</p>
 * <ul>
 *   <li>1. Rotasyon (K1): Her yenilemede eski token silinmez, replacedAt damgası alır ve yeni token üretilir.</li>
 *   <li>2. Yeniden Kullanım Tespiti (K2): Damgalı bir token tekrar sunulursa kullanıcının tüm oturum ailesi silinir.</li>
 *   <li>3. Sabit Tavan (K3): Rotasyon sırasında expiryDate uzatılmaz, eski satırdan aynen devralınır.</li>
 *   <li>4. Hatırlama Durumu (K4): remembered seçimi rotasyonda yeni satıra aktarılır.</li>
 *   <li>5. Bilgi Sızdırmama (K5): Yeniden kullanımda istemciye genel 401 Unauthorized döner.</li>
 *   <li>6. Eşzamanlı Oturum: Yalnızca replacedAt == null olan aktif oturumlar 3 sınırına dahil edilir.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    /**
     * createRefreshToken: Kullanıcı giriş yaptığında yeni bir oturum oluşturur.
     * Kullanıcının süresi dolmuş eski satırlarını temizler ve aktif oturum sınırını (3) denetler.
     *
     * @param user Oturum açan kullanıcı
     * @param rememberMe Beni hatırla seçimi (true: 30 gün, false: 1 gün oturum çerezi)
     * @return Üretilen refresh token dizesi
     */
    @Transactional
    public String createRefreshToken(AppUser user, boolean rememberMe) {
        // 1. Süresi geçmiş eski satırları temizle (Zamanlayıcı yerine girişte tek sorgu)
        refreshTokenRepository.findAllByUser(user).stream()
                .filter(t -> t.getExpiryDate().isBefore(Instant.now()))
                .forEach(refreshTokenRepository::delete);

        // 2. Yalnızca aktif (damgasız) oturumları kontrol et (K1)
        List<RefreshToken> activeSessions = refreshTokenRepository.findAllByUserAndReplacedAtIsNullOrderByExpiryDateAsc(user);

        // 3. Eğer 3 veya daha fazla aktif oturum varsa, en eski aktif olanları temizle
        if (activeSessions.size() >= 3) {
            int sessionsToRemove = activeSessions.size() - 2; // Yeniye yer açmak için
            for (int i = 0; i < sessionsToRemove; i++) {
                refreshTokenRepository.delete(activeSessions.get(i));
            }
        }

        long duration = rememberMe 
                ? jwtService.getRememberedRefreshExpiration() 
                : jwtService.getShortRefreshExpiration();

        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken(
                token,
                user,
                Instant.now().plusMillis(duration),
                null,
                rememberMe
        );

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    /**
     * Geriye dönük uyumluluk için varsayılan rememberMe = false ile oturum açar.
     */
    @Transactional
    public String createRefreshToken(AppUser user) {
        return createRefreshToken(user, false);
    }

    /**
     * rotate: Sunulan refresh token'ı doğrular, eskisini damgalar (replacedAt) ve yeni token üretir (K1).
     * <p>Eğer sunulan token daha önce rotasyona uğramışsa (replacedAt != null), bu bir hırsızlık/replay atağı
     * olarak kabul edilir ve kullanıcının tüm oturum ailesi derhal iptal edilir (K2 - Token Family Revocation).</p>
     * 
     * <p>K3 & K4 (Sabit Tavan & Devir): Yeni satır, eski satırın expiryDate ve remembered değerini
     * aynen devralır. Böylece oturum süresi uzamaz ve ilk girişten itibaren belirlenen tavan süre korunur.</p>
     * 
     *
     *
     * @param oldTokenStr İstemcinin sunduğu mevcut refresh token
     * @return Üretilen yeni RefreshToken nesnesi
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshToken rotate(String oldTokenStr) {
        if (oldTokenStr == null || oldTokenStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı.");
        }

        // 1. Token varlık kontrolü
        RefreshToken oldToken = refreshTokenRepository.findByToken(oldTokenStr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya süresi doldu."));

        // 2. Yeniden Kullanım Tespiti (ENG-11 §2.3 / K2): Damgalı token tekrar sunuldu -> Tüm aileyi düşür
        if (oldToken.getReplacedAt() != null) {
            deleteByUserId(oldToken.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum süresi doldu. Lütfen tekrar giriş yapın.");
        }

        // 3. Süre Aşımı Kontrolü
        if (oldToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(oldToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum süresi doldu. Lütfen tekrar giriş yapın.");
        }

        // 4. Eskiyi Damgala (Rotasyon - K1)
        oldToken.setReplacedAt(Instant.now());
        refreshTokenRepository.save(oldToken);

        // 5. Yeni Refresh Token Üret ve Kaydet (K3 & K4: expiryDate ve remembered devredilir)
        String newToken = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = new RefreshToken(
                newToken,
                oldToken.getUser(),
                oldToken.getExpiryDate(), // K3: Süre uzatılmaz, eski satırdan devralınır
                null,
                oldToken.isRemembered()   // K4: Hatırlama bayrağı devralınır
        );

        return refreshTokenRepository.save(newRefreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * deleteByUserId: Kullanıcının tüm refresh token kayıtlarını siler.
     *
     * @param user Kullanıcı entity'si
     * @return Silinen kayıt sayısı
     */
    @Transactional
    public int deleteByUserId(AppUser user) {
        return refreshTokenRepository.deleteByUser(user);
    }
}
