package com.hankabakc.analyzepanel.core.exception;

import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler: Uygulama genelinde fırlatılan tüm istisnaları (Exception)
 * merkezi bir noktadan yakalar ve kullanıcıya ApiResponse formatında standart bir hata döner.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;

    public GlobalExceptionHandler(AuditLogRepository auditLogRepository, SecurityUtils securityUtils) {
        this.auditLogRepository = auditLogRepository;
        this.securityUtils = securityUtils;
    }

    /**
     * logSecurityViolation: Yetkisiz erişim denemelerini audit log tablosuna kaydeder.
     */
    private void logSecurityViolation(HttpServletRequest request, String message) {
        String email = securityUtils.getCurrentUserEmail();
        if (email == null) email = "ANONYMOUS";
        
        AuditLog log = new AuditLog(
            "SECURITY_VIOLATION",
            email,
            request.getRemoteAddr(),
            "Girişim Engellendi: " + message + " (URL: " + request.getRequestURI() + ")"
        );
        auditLogRepository.save(log);
    }

    @ExceptionHandler(com.hankabakc.analyzepanel.auth.exception.AccountRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountRateLimit(
            com.hankabakc.analyzepanel.auth.exception.AccountRateLimitException e,
            HttpServletRequest request) {
        logSecurityViolation(request, "Hesap Kaba Kuvvet Koruması: " + e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        // Hatalı giriş denemelerini de güvenlik ihlali olarak kaydedelim
        logSecurityViolation(request, "Hatalı Giriş Denemesi");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("E-posta veya şifre hatalı."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        logSecurityViolation(request, "Yetkisiz Roller Arası Erişim");
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Bu işlem için yetkiniz bulunmamaktadır."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Doğrulama hatası: " + errorMessage));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        // Durum kodunu kendisi taşıyan 4xx hataları (ör. ResponseStatusException ile
        // bildirilen "oturum süresi doldu") istemci kaynaklıdır; raporlanmaz.
        ResponseEntity<ApiResponse<Void>> clientError = asClientError(e);
        if (clientError != null) return clientError;

        // validateAccess tarafından fırlatılan yetki hatalarını yakala ve logla
        if (e.getMessage() != null && (e.getMessage().contains("yetkiniz yok") || e.getMessage().contains("erişebilirsiniz"))) {
            logSecurityViolation(request, "IDOR/BOLA Girişimi: " + e.getMessage());
        }

        io.sentry.Sentry.captureException(e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }


    /**
     * handleUnreadableBody: Bozuk veya okunamayan istek gövdesi.
     *
     * <p>Gönderilen JSON'ın hatalı olması istemci tarafının sorunudur; sunucuda
     * düzeltilecek bir şey yoktur, bu yüzden Sentry'ye raporlanmaz.</p>
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("İstek gövdesi okunamadı. Geçerli bir JSON gönderiniz."));
    }


    /**
     * asClientError: Hata kendi durum kodunu taşıyor ve bu kod 4xx ise, istemci
     * kaynaklı sayılır ve hazır bir yanıt döndürülür; aksi halde null döner.
     *
     * <p>Olmayan yol, eksik parametre, desteklenmeyen içerik türü, süresi dolmuş
     * oturum... Bunlarda sunucuda düzeltilecek bir şey yoktur. Sentry'ye
     * gönderilmezler; aksi halde her bot taraması bir hata kaydı üretirdi.</p>
     */
    private ResponseEntity<ApiResponse<Void>> asClientError(Exception e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException rse && rse.getStatusCode().is4xxClientError() && rse.getReason() != null && !rse.getReason().isBlank()) {
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(rse.getStatusCode());
            rse.getHeaders().forEach((headerName, headerValues) -> {
                for (String headerValue : headerValues) {
                    builder.header(headerName, headerValue);
                }
            });
            return builder.body(ApiResponse.error(rse.getReason()));
        }
        if (!(e instanceof org.springframework.web.ErrorResponse er) || !er.getStatusCode().is4xxClientError()) {
            return null;
        }
        int status = er.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "Oturum bulunamadı veya süresi doldu.";
            case 404 -> "İstenen kaynak bulunamadı.";
            default -> "Geçersiz istek.";
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(er.getStatusCode());
        er.getHeaders().forEach((headerName, headerValues) -> {
            for (String headerValue : headerValues) {
                builder.header(headerName, headerValue);
            }
        });
        return builder.body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        ResponseEntity<ApiResponse<Void>> clientError = asClientError(e);
        if (clientError != null) return clientError;

        // Kullanıcıya genel bir mesaj döner; asıl yığın izi Sentry'ye gider.
        io.sentry.Sentry.captureException(e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Sistemsel bir hata oluştu. Lütfen daha sonra tekrar deneyiniz."));
    }
}
