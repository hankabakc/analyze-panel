package com.hankabakc.analyzepanel.auth.controller;

import com.hankabakc.analyzepanel.auth.dto.ChangePasswordRequest;
import com.hankabakc.analyzepanel.auth.dto.LoginRequest;
import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.RefreshToken;
import com.hankabakc.analyzepanel.auth.service.AuthService;
import com.hankabakc.analyzepanel.auth.service.JwtService;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.annotation.AuditAction;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * AuthController: Kimlik doğrulama, E-Posta + Şifre ile giriş,
 * şifre değişikliği ve oturum yönetimi uç noktalarını sunar (T-056).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;

    // T-069: Çerez bayrakları ortamdan okunur. Canlıda Secure zorunlu (çerez yalnızca HTTPS ile taşınır);
    // yerelde HTTP ile çalışıldığı için varsayılan kapalıdır.
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          JwtService jwtService,
                          SecurityUtils securityUtils) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.securityUtils = securityUtils;
    }

    /**
     * POST /api/v1/auth/login: E-posta ve şifre ile oturum açar (T-009 / K1).
     * Başarılıysa JWT çerezlerini (access_token, refresh_token) yazar.
     */
    @PostMapping("/login")
    @AuditAction("LOGIN")
    public ApiResponse<UserDto> login(@Valid @RequestBody LoginRequest request,
                                      HttpServletResponse response) {
        Map<String, Object> authData = authService.login(request);

        String accessToken = (String) authData.get("accessToken");
        String refreshToken = (String) authData.get("refreshToken");
        boolean rememberMe = (Boolean) authData.getOrDefault("rememberMe", false);
        UserDto user = (UserDto) authData.get("user");

        int refreshMaxAge = rememberMe ? jwtService.getRememberedRefreshExpirationInSeconds() : -1;

        addCookie(response, "access_token", accessToken, jwtService.getJwtExpirationInSeconds());
        addCookie(response, "refresh_token", refreshToken, refreshMaxAge);

        return ApiResponse.success(user, "Giriş başarılı.");
    }

    /**
     * POST /api/v1/auth/password/change: Oturumdaki kullanıcının şifresini değiştirir (T-009 / K2, K5).
     * Şifre değiştiğinde kullanıcının tüm refresh token'ları silinir ve yeni oturum çerezleri yazılır.
     */
    @PostMapping("/password/change")
    @AuditAction("CHANGE_PASSWORD")
    public ApiResponse<UserDto> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletResponse response) {
        String email = securityUtils.getCurrentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya süresi doldu.");
        }

        Map<String, Object> authData = authService.changePassword(email, request);

        String accessToken = (String) authData.get("accessToken");
        String refreshToken = (String) authData.get("refreshToken");
        UserDto user = (UserDto) authData.get("user");

        addCookie(response, "access_token", accessToken, jwtService.getJwtExpirationInSeconds());
        addCookie(response, "refresh_token", refreshToken, -1);

        return ApiResponse.success(user, "Şifreniz başarıyla değiştirildi.");
    }

    /**
     * GET /api/v1/auth/me: Oturumdaki kullanıcının profil bilgilerini döner.
     * Oturum yoksa 401 Unauthorized döner (T-002).
     * T-056: Mevcut şifre yalnızca mustChangePassword=true iken sorulmaz; normal oturumlarda zorunludur.
     */
    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        AppUser user = securityUtils.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya süresi doldu.");
        }
        boolean currentPasswordRequired = !user.isMustChangePassword();
        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getGrade(),
                user.isMustChangePassword(),
                currentPasswordRequired
        );
        return ApiResponse.success(userDto, "Kullanıcı bilgileri alındı.");
    }

    /**
     * POST /api/v1/auth/refresh: Süresi dolan access token'ı geçerli refresh token ile yeniler.
     * T-006: Her yenilemede refresh token rotasyona uğrar ve yeni refresh token çereze yazılır.
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenStr = jwtService.extractTokenFromCookie(request, "refresh_token");

        if (refreshTokenStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı.");
        }

        // 1. Refresh Token Rotasyonu ve Yeniden Kullanım Tespiti (K1, K2, K3, K4)
        RefreshToken newRefreshToken = refreshTokenService.rotate(refreshTokenStr);

        // 2. Yeni Access Token üret
        String newAccessToken = jwtService.generateToken(newRefreshToken.getUser().getEmail());

        // 3. T-007 / K5: Çerez süresini hesapla (remembered ise kalan saniye, değilse -1 oturum çerezi)
        int refreshMaxAge;
        if (newRefreshToken.isRemembered()) {
            long remainingSeconds = (newRefreshToken.getExpiryDate().toEpochMilli() - System.currentTimeMillis()) / 1000;
            refreshMaxAge = (int) Math.max(1, remainingSeconds);
        } else {
            refreshMaxAge = -1; // Oturum çerezi (K5)
        }

        // 4. Yeni Access Token ve yeni Refresh Token çerezlerini yaz
        addCookie(response, "access_token", newAccessToken, jwtService.getJwtExpirationInSeconds());
        addCookie(response, "refresh_token", newRefreshToken.getToken(), refreshMaxAge);

        return ApiResponse.success(null, "Token yenilendi.");
    }

    /**
     * POST /api/v1/auth/logout: Kullanıcı oturumunu sonlandırır ve tarayıcı çerezlerini temizler.
     */
    @PostMapping("/logout")
    @AuditAction("LOGOUT")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = jwtService.extractTokenFromCookie(request, "access_token");
        String refreshToken = jwtService.extractTokenFromCookie(request, "refresh_token");

        authService.logout(accessToken, refreshToken);

        // Çerezleri temizle
        addCookie(response, "access_token", null, 0);
        addCookie(response, "refresh_token", null, 0);

        // Tarayıcı hafızasını tamamen imha et (OWASP 2026: Anti-Forensics)
        response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");

        return ApiResponse.success(null, "Oturum başarıyla kapatıldı.");
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setAttribute("SameSite", cookieSameSite);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
