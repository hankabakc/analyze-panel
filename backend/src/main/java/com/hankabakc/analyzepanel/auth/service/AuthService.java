package com.hankabakc.analyzepanel.auth.service;

import com.hankabakc.analyzepanel.auth.dto.ChangePasswordRequest;
import com.hankabakc.analyzepanel.auth.dto.LoginRequest;
import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.entity.BlacklistedToken;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.repository.BlacklistedTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * AuthService: Kimlik doğrulama, e-posta + şifre girişi,
 * şifre değişikliği ve güvenli çıkış süreçlerini yöneten temel iş mantığı servisidir (T-056).
 */
@Service
public class AuthService {

    // B-19 & K1: Zamanlama saldırılarını (Timing Attack) önlemek için kullanılan biçimsel olarak geçerli sahte BCrypt cost 12 hash'i
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository userRepository;
    private final com.hankabakc.analyzepanel.membership.service.ActivityService activityService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordBreachChecker passwordBreachChecker;
    private final LoginAttemptService loginAttemptService;
    private final com.hankabakc.analyzepanel.core.security.SecurityUtils securityUtils;
    private final com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository;

    public AuthService(AppUserRepository userRepository,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       BlacklistedTokenRepository blacklistedTokenRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordBreachChecker passwordBreachChecker,
                       com.hankabakc.analyzepanel.membership.service.ActivityService activityService,
                       LoginAttemptService loginAttemptService,
                       com.hankabakc.analyzepanel.core.security.SecurityUtils securityUtils,
                       com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordBreachChecker = passwordBreachChecker;
        this.activityService = activityService;
        this.loginAttemptService = loginAttemptService;
        this.securityUtils = securityUtils;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * login: E-posta ve şifre ile güvenli oturum açma işlemini yürütür (T-009 / K1).
     * ENG-11 §1.2 & T-009B / K1 (B-19): Kullanıcı bulunamasa veya şifresi null olsa dahi
     * sahte hash üzerinden passwordEncoder.matches çalıştırılarak süre farkı (Timing Attack) sıfırlanır.
     * ENG-11 §1.3 & T-056B: E-posta bazlı kaba kuvvet ve kademeli gecikme denetlenir.
     *
     * @param request E-posta, şifre ve beni hatırla verilerini içeren LoginRequest
     * @return Oturum token'ları ve UserDto verilerini içeren harita
     */
    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        // T-056B & ENG-11 §1.3: Hesap bazlı kademeli gecikme (Exponential Backoff) denetimi
        loginAttemptService.checkRateLimit(request.email());

        Optional<AppUser> userOpt = userRepository.findByEmail(request.email());

        // K1 & ENG-11 §1.2: Zamanlama saldırısını (Timing Attack) önlemek için kullanıcı olmasa da
        // veya şifresi null olsa da BCrypt hash karşılaştırması sahte bir hash üzerinden mutlak çalıştırılır.
        boolean userExistsWithPassword = userOpt.isPresent() && userOpt.get().getPassword() != null;
        String hashToCompare = userExistsWithPassword ? userOpt.get().getPassword() : DUMMY_BCRYPT_HASH;
        boolean matches = passwordEncoder.matches(request.password(), hashToCompare);

        if (!userExistsWithPassword || !matches) {
            // T-056B & ENG-11 §1.2 & §1.3: Hatalı deneme sayacı artırılır; 5. hatada kademeli gecikme başlar
            loginAttemptService.recordFailure(request.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı.");
        }

        AppUser user = userOpt.get();

        // K2, K3: Ortak hesap durumu denetimi
        validateUserActiveStatus(user);

        // Başarılı girişte hesap kaba kuvvet sayacı sıfırlanır
        loginAttemptService.recordSuccess(request.email());

        activityService.recordLogin(user.getId()); // T-037: aktiflik kaydı (hatası girişi düşürmez)

        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user, request.isRememberMe());
        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getGrade(),
                user.isMustChangePassword(),
                !user.isMustChangePassword()
        );

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "rememberMe", request.isRememberMe(),
                "user", userDto
        );
    }

    /**
     * changePassword: Oturum açmış kullanıcının şifresini güvenle değiştirmesini sağlar (T-009 / K2, K3, K5).
     * ENG-11 §2.4: Şifre değiştiğinde kullanıcının mevcut tüm refresh token'ları iptal edilir ve yeni token verilir.
     *
     * @param email Oturumdaki kullanıcının e-postası
     * @param request Mevcut ve yeni şifre DTO'su
     * @return Yeni oturum verilerini içeren harita
     */
    @Transactional
    public Map<String, Object> changePassword(String email, ChangePasswordRequest request) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));

        // T-015 & T-056: Mevcut şifre kontrolü yalnızca mustChangePassword = false olan normal kullanıcılar için zorunludur.
        // mustChangePassword = true olan ilk girişli kullanıcı muaf tutulur (APP-01 §2.1).
        if (user.getPassword() != null && !user.isMustChangePassword()) {
            if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mevcut şifre hatalı.");
            }
        }

        // T-015 / ENG-11 §1.1: Yeni şifre eski/geçici şifre ile aynı olamaz (Hash üzerinden denetlenir)
        if (user.getPassword() != null && passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yeni şifreniz mevcut şifrenizle aynı olamaz.");
        }

        // K3: Sızıntı kontrolü
        if (passwordBreachChecker.isBreached(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu şifre daha önce sızdırılmış veri tabanlarında bulundu. Lütfen farklı ve güvenli bir şifre seçin.");
        }

        // Şifreyi BCrypt cost 12 ile hash'le ve kaydet
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setCredentialsInvalidatedAt(java.time.Instant.now());
        userRepository.save(user);

        // ENG-11 §2.4: Şifre değiştiğinde tüm refresh token'lar silinir (aile iptali)
        refreshTokenService.deleteByUserId(user);

        // Yeni oturum token'ları oluştur
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user, false);
        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getGrade(),
                user.isMustChangePassword(),
                !user.isMustChangePassword()
        );

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "rememberMe", false,
                "user", userDto
        );
    }

    private String getClientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    /**
     * validateUserActiveStatus: Kullanıcının hesap durumunun ACTIVE olup olmadığını denetler (K2, K3 / ENG-12 §6).
     *
     * @param user Denetlenecek kullanıcı
     */
    private void validateUserActiveStatus(AppUser user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesabınız aktif değil.");
        }
    }

    /**
     * logout: Kullanıcının oturumunu tam güvenli olarak kapatır.
     * Refresh token'ı siler ve mevcut Access token'ı kara listeye alır.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.findByToken(refreshToken).ifPresent(token -> {
                refreshTokenService.deleteByUserId(token.getUser());
            });
        }

        if (accessToken != null && !accessToken.isBlank()) {
            LocalDateTime expiry = jwtService.extractExpirationDateTime(accessToken);
            BlacklistedToken blacklistedToken = new BlacklistedToken(accessToken, expiry);
            blacklistedTokenRepository.save(blacklistedToken);
        }
    }
}
