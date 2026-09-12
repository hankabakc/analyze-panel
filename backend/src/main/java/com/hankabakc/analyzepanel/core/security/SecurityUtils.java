package com.hankabakc.analyzepanel.core.security;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SecurityUtils: Mevcut oturumdaki kullanıcı bilgilerine erişim sağlayan yardımcı sınıftır.
 * IDOR koruması için her istekte "Bu veri bu kullanıcıya mı ait?" kontrolü burada başlar.
 */
@Component
public class SecurityUtils {

    private final AppUserRepository userRepository;

    public SecurityUtils(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * getCurrentUserEmail: Oturumdaki kullanıcının e-posta adresini döner.
     * Kullanıcı doğrulanmamışsa veya anonim ise null döner.
     */
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * getCurrentUser: Oturumdaki kullanıcıyı veritabanından yükleyerek döner.
     * Oturum yoksa null döner.
     */
    public AppUser getCurrentUser() {
        String email = getCurrentUserEmail();
        if (email == null) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }
}
