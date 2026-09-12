package com.hankabakc.analyzepanel.core.audit.aspect;

import com.hankabakc.analyzepanel.core.audit.annotation.AuditAction;
import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AuditAspect: @AuditAction anotasyonunu izler ve otomatik günlük kaydı tutar.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;
    private final com.hankabakc.analyzepanel.auth.repository.AppUserRepository userRepository;

    public AuditAspect(AuditLogRepository auditLogRepository, SecurityUtils securityUtils,
                       com.hankabakc.analyzepanel.auth.repository.AppUserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.securityUtils = securityUtils;
        this.userRepository = userRepository;
    }

    @AfterReturning(pointcut = "@annotation(auditAction)", returning = "result")
    public void logAction(JoinPoint joinPoint, AuditAction auditAction, Object result) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        
        java.util.UUID userId = null;
        String userEmail = null;

        // 1. Önce SecurityUtils üzerinden oturumdaki kullanıcıyı al (T-042)
        com.hankabakc.analyzepanel.auth.entity.AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getId();
        } else {
            // 2. Henüz SecurityContext oluşmadıysa (login gibi uçlar),
            // yanıt gövdesinden ID'yi al (T-041C / T-042: id doğrudan UserDto içinde mevcuttur)
            if (result instanceof com.hankabakc.analyzepanel.core.model.ApiResponse<?> apiResponse
                    && apiResponse.data() instanceof com.hankabakc.analyzepanel.auth.dto.UserDto userDto) {
                userId = userDto.id();
            }

            // 3. Eğer hala userId bulunamadıysa SecurityContext authentication ismine bak
            if (userId == null) {
                String authName = securityUtils.getCurrentUserEmail();
                if (authName != null) {
                    // Eğer authName bir e-posta değilse (örn. testlerdeki "user" gibi bir rol/yer tutucu)
                    if (!authName.contains("@")) {
                        userEmail = authName; // "user" PII değildir
                    } else {
                        // Eğer e-posta formatındaysa (@WithMockUser testleri gibi), veritabanından ID'sini ara
                        var userByEmail = userRepository.findByEmail(authName);
                        if (userByEmail.isPresent()) {
                            userId = userByEmail.get().getId();
                        } else {
                            // Veritabanında bile yoksa e-posta ASLA düz metin yazılamaz (APP-01 §2.2 / T-042)
                            userEmail = "ANONYMOUS";
                        }
                    }
                }
            }
        }

        // 4. Hiçbir kimlik tespit edilemediyse yer tutucu atanır
        if (userId == null && userEmail == null) {
            userEmail = "ANONYMOUS";
        }
        
        String ipAddress = request.getRemoteAddr();
        String action = auditAction.value();
        
        // İşlem detaylarını argümanlardan alalım.
        //
        // GÜVENLİK: Argümanlar olduğu gibi yazılamaz. İstek DTO'ları hassas veri (şifre,
        // token vb.) taşıyabileceğinden toString() çıktıları veritabanına loglanamaz.
        // Bu yüzden yalnızca basit güvenli değerler (id, sayı, bayrak) kaydedilir; DTO'lar,
        // servlet nesneleri ve dosyalar atlanır. Zaten anlamlı olan kısım da bunlar.
        StringBuilder details = new StringBuilder();
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (isSafeToLog(arg)) details.append(arg).append(" ");
            }
        }

        AuditLog log = new AuditLog(action, userId, userEmail, ipAddress, sanitizeForLog(details.toString().trim()));
        auditLogRepository.save(log);
    }

    /**
     * isSafeToLog: Yalnızca kendi başına anlamlı ve hassas veri taşımayan basit
     * değerlerin kaydedilmesine izin verir. İstek DTO'ları (şifre içerebilir),
     * servlet nesneleri ve yüklenen dosyalar dışarıda kalır.
     */
    private boolean isSafeToLog(Object arg) {
        return arg instanceof java.util.UUID
                || arg instanceof Number
                || arg instanceof Boolean
                || arg instanceof Enum<?>
                || arg instanceof CharSequence;
    }

    /**
     * sanitizeForLog: Log Forging saldırılarını engellemek için yeni satır karakterlerini temizler.
     */
    private String sanitizeForLog(String input) {
        if (input == null) return "";
        return input.replace("\n", "[NL]").replace("\r", "[CR]");
    }
}
