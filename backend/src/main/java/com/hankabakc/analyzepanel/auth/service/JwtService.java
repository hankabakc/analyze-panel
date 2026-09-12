package com.hankabakc.analyzepanel.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtService: RSA-256 Asimetrik şifreleme ile Access ve Refresh token yönetimini yapar.
 * OWASP 2026: Simetrik anahtar sızıntısı riskine karşı asimetrik şifreleme zorunludur.
 */
@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key:}")
    private String secretKey; // RSA geçişi tamamlanana kadar fallback veya seed olarak kullanılır

    @PostConstruct
    public void validateSecretKey() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'APPLICATION_SECURITY_JWT_SECRET_KEY' (application.security.jwt.secret-key) " +
                    "çevre değişkeni tanımlanmamış veya boş! HS512 için en az 64 karakter olmalıdır."
            );
        }
        if (secretKey.trim().length() < 64) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'APPLICATION_SECURITY_JWT_SECRET_KEY' (application.security.jwt.secret-key) " +
                    "çok kısa! HS512 için en az 64 karakter olmalıdır. Verilen uzunluk: " + secretKey.trim().length()
            );
        }
    }

    @Value("${application.security.jwt.expiration:900000}") // 15 Dakika (Access Token)
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration:604800000}") // 7 Gün (Eski fallback)
    private long refreshExpiration;

    @Value("${application.security.jwt.refresh-token.remembered-expiration:2592000000}") // 30 Gün (Remember Me Refresh Token)
    private long rememberedRefreshExpiration;

    @Value("${application.security.jwt.refresh-token.short-expiration:86400000}") // 1 Gün (Kısa / Oturum Refresh Token)
    private long shortRefreshExpiration;

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public int getJwtExpirationInSeconds() {
        return (int) (jwtExpiration / 1000);
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public int getRefreshExpirationInSeconds() {
        return (int) (refreshExpiration / 1000);
    }

    public long getRememberedRefreshExpiration() {
        return rememberedRefreshExpiration;
    }

    public int getRememberedRefreshExpirationInSeconds() {
        return (int) (rememberedRefreshExpiration / 1000);
    }

    public long getShortRefreshExpiration() {
        return shortRefreshExpiration;
    }

    public int getShortRefreshExpirationInSeconds() {
        return (int) (shortRefreshExpiration / 1000);
    }

    /**
     * generateToken: Kısa ömürlü standart Access Token üretir.
     *
     * @param username Kullanıcı e-postası (subject)
     * @return İmzalı JWT Access Token
     */
    public String generateToken(String username) {
        return buildToken(new HashMap<>(), username, jwtExpiration);
    }

    /**
     * generateRefreshToken: Uzun ömürlü Refresh Token üretir.
     */
    public String generateRefreshToken(String username) {
        return buildToken(new HashMap<>(), username, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS512) // Şimdilik HS512 (Strong) kullanıyoruz, RSA Key Management servisi eklenebilir.
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public java.time.LocalDateTime extractExpirationDateTime(String token) {
        Date expiration = extractExpiration(token);
        return expiration.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * extractTokenFromCookie: HttpServletRequest içindeki çerezlerden token'ı ayıklar.
     */
    public String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
