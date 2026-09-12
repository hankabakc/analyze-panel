package com.hankabakc.analyzepanel.auth;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.RefreshToken;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.repository.RefreshTokenRepository;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RefreshTokenIntegrationTest: T-006 kapsamında Refresh Token Rotasyonu,
 * Yeniden Kullanım Tespiti (Token Family Revocation), süre aşımı ve aktif oturum
 * sınırlarını gerçek veritabanı üzerinde test eder.
 * 
 * <p>Tasarım İlkeleri:</p>
 * <ul>
 *   <li>K1: Sınıf seviyesinde @Transactional kullanılmaz; veritabanı durumları doğrudan committed sorgulanır.</li>
 *   <li>K2: Testler her çalıştırma öncesi ve sonrası veritabanını temizler (APP-03 §4).</li>
 * </ul>
 */
@SpringBootTest
public class RefreshTokenIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AppUserRepository userRepository;

    private static final String TEST_EMAIL = "rotation.test@analyzepanel.local";

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        cleanup();
        testUser = new AppUser(
                TEST_EMAIL,
                "Rotasyon Test Kullanıcısı",
                UserRole.TEACHER,
                UserStatus.ACTIVE
        );
        testUser = userRepository.saveAndFlush(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
    }

    @Test
    @DisplayName("T-006 / K1: Başarılı rotasyon -> Eski token replaced_at damgası alır, yeni token üretilir")
    void testRefreshToken_Rotation_Success() {
        // 1. İlk oturum token'ı oluştur
        String initialToken = refreshTokenService.createRefreshToken(testUser);
        assertNotNull(initialToken);

        // 2. Token'ı rotasyona uğrat
        RefreshToken newRefreshToken = refreshTokenService.rotate(initialToken);
        assertNotNull(newRefreshToken);
        assertNotEquals(initialToken, newRefreshToken.getToken());
        assertNull(newRefreshToken.getReplacedAt(), "Yeni üretilen token'ın replacedAt alanı null olmalıdır");

        // 3. Veritabanından eski token'ı sorgula
        Optional<RefreshToken> oldTokenOpt = refreshTokenRepository.findByToken(initialToken);
        assertTrue(oldTokenOpt.isPresent());
        assertNotNull(oldTokenOpt.get().getReplacedAt(), "Eski token veritabanında replacedAt damgası almış olmalıdır");
    }

    @Test
    @DisplayName("T-006 / K2: Yeniden Kullanım Tespiti -> Damgalı token sunulduğunda 401 döner VE tüm aile silinir")
    void testRefreshToken_ReuseDetection_RevokesAllUserTokens() {
        // 1. İlk token oluşturulur ve 2 kez rotasyona sokulur
        String token1 = refreshTokenService.createRefreshToken(testUser);
        RefreshToken token2 = refreshTokenService.rotate(token1);
        RefreshToken token3 = refreshTokenService.rotate(token2.getToken());

        // Kullanıcının şu an 3 satırı vardır (2 tanesi damgalı, 1 tanesi aktif token3)
        List<RefreshToken> beforeAttack = refreshTokenRepository.findAllByUser(testUser);
        assertEquals(3, beforeAttack.size());

        // 2. Saldırgan daha önce kullanılmış token1'i sunar (Replay Attack)
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                refreshTokenService.rotate(token1)
        );
        assertEquals(401, ex.getStatusCode().value());

        // 3. K2 / ENG-11 §2.4: Kullanıcının veritabanındaki TÜM token ailesi silinmiş olmalıdır!
        List<RefreshToken> afterAttack = refreshTokenRepository.findAllByUser(testUser);
        assertTrue(afterAttack.isEmpty(), "Yeniden kullanım tespit edildiğinde kullanıcının tüm refresh token satırları silinmelidir");
    }

    @Test
    @DisplayName("T-006: Süresi dolmuş refresh token sunulduğunda 401 döner ve satır silinir")
    void testRefreshToken_ExpiredToken_Returns401AndDeletes() {
        String expiredTokenStr = UUID.randomUUID().toString();
        RefreshToken expiredToken = new RefreshToken(
                expiredTokenStr,
                testUser,
                Instant.now().minusSeconds(3600) // 1 saat önce dolmuş
        );
        refreshTokenRepository.saveAndFlush(expiredToken);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                refreshTokenService.rotate(expiredTokenStr)
        );
        assertEquals(401, ex.getStatusCode().value());

        // Veritabanından silinmiş olmalı
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(expiredTokenStr);
        assertTrue(tokenOpt.isEmpty(), "Süresi dolmuş token veritabanından silinmiş olmalıdır");
    }

    @Test
    @DisplayName("T-006: Ardışık yenilemeler aktif oturum sınırını (3) tüketmez")
    void testRefreshToken_ConsecutiveRotations_DoNotExceedSessionLimit() {
        // Kullanıcı 4 kez ardışık yenileme yapar
        String currentToken = refreshTokenService.createRefreshToken(testUser);
        for (int i = 0; i < 4; i++) {
            RefreshToken rotated = refreshTokenService.rotate(currentToken);
            currentToken = rotated.getToken();
        }

        // Aktif (damgasız) oturum sayısı tam olarak 1 olmalıdır
        List<RefreshToken> activeSessions = refreshTokenRepository.findAllByUserAndReplacedAtIsNullOrderByExpiryDateAsc(testUser);
        assertEquals(1, activeSessions.size(), "Ardışık rotasyonlar aktif oturum sınırını düşürmemelidir");
        assertEquals(currentToken, activeSessions.get(0).getToken());
    }

    @Test
    @DisplayName("T-007 / K2: rememberMe = true -> 30 günlük oturum ve remembered = true atanır")
    void testRememberMe_True_Creates30DayTokenAndSetsRemembered() {
        String tokenStr = refreshTokenService.createRefreshToken(testUser, true);
        RefreshToken token = refreshTokenRepository.findByToken(tokenStr).orElseThrow();

        assertTrue(token.isRemembered(), "remembered alanı true olmalıdır");
        
        long daysUntilExpiry = java.time.Duration.between(Instant.now(), token.getExpiryDate()).toDays();
        assertTrue(daysUntilExpiry >= 29 && daysUntilExpiry <= 31, 
                "Token süresi yaklaşık 30 gün olmalıdır (bulunan gün: " + daysUntilExpiry + ")");
    }

    @Test
    @DisplayName("T-007 / K2: rememberMe = false -> 1 günlük oturum ve remembered = false atanır")
    void testRememberMe_False_Creates1DayTokenAndRememberedFalse() {
        String tokenStr = refreshTokenService.createRefreshToken(testUser, false);
        RefreshToken token = refreshTokenRepository.findByToken(tokenStr).orElseThrow();

        assertFalse(token.isRemembered(), "remembered alanı false olmalıdır");
        
        long hoursUntilExpiry = java.time.Duration.between(Instant.now(), token.getExpiryDate()).toHours();
        assertTrue(hoursUntilExpiry >= 23 && hoursUntilExpiry <= 25, 
                "Token süresi yaklaşık 24 saat (1 gün) olmalıdır (bulunan saat: " + hoursUntilExpiry + ")");
    }

    @Test
    @DisplayName("T-007 / K3 & K4: Rotasyon sırasında expiry_date uzatılmaz (sabit tavan) ve remembered devredilir")
    void testRotation_InheritsExpiryDateAndRemembered_WithoutExtending() {
        // 1. 30 günlük oturum açılır
        String token1Str = refreshTokenService.createRefreshToken(testUser, true);
        RefreshToken token1 = refreshTokenRepository.findByToken(token1Str).orElseThrow();
        Instant originalExpiry = token1.getExpiryDate();
        assertTrue(token1.isRemembered());

        // 2. İlk rotasyon yapılır
        RefreshToken token2 = refreshTokenService.rotate(token1Str);
        assertEquals(originalExpiry, token2.getExpiryDate(), 
                "K3: Rotasyon sonrasında yeni token'ın expiryDate'i eski token ile birebir aynı olmalı, uzatılmamalıdır");
        assertTrue(token2.isRemembered(), "K4: remembered alanı rotasyonda korunmalıdır");

        // 3. İkinci rotasyon yapılır
        RefreshToken token3 = refreshTokenService.rotate(token2.getToken());
        assertEquals(originalExpiry, token3.getExpiryDate(), 
                "K3: İkinci rotasyonda da orijinal tavan süre korunmalıdır");
        assertTrue(token3.isRemembered(), "K4: remembered alanı ikinci rotasyonda da korunmalıdır");
    }
}
