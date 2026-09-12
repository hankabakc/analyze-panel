package com.hankabakc.analyzepanel.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * MockPasswordBreachChecker: Geliştirme ve test ortamlarında harici HaveIBeenPwned servisine
 * bağımlı kalmadan sızıntı denetimi yapılmasını sağlayan sahte adaptördür.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.6 & K3: Mock-önce yaklaşımı ile küçük bir sızdırılmış şifre listesi üzerinden karar verir.</li>
 *   <li>APP-01 §2.4: Servis hata verirse veya bağlantı kesilirse şifre KABUL EDİLİR (fail-open) ve olay loglanır.</li>
 * </ul>
 */
@Component
@Profile("!prod")
public class MockPasswordBreachChecker implements PasswordBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(MockPasswordBreachChecker.class);

    private static final Set<String> KNOWN_BREACHED_PASSWORDS = Set.of(
            "password123456",
            "123456789012",
            "qwerty123456",
            "admin12345678",
            "supersecret12"
    );

    private boolean simulateServiceError = false;

    @Override
    public boolean isBreached(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        try {
            if (simulateServiceError) {
                throw new RuntimeException("Sızıntı denetim servisi zaman aşımına uğradı (Simülasyon).");
            }

            return KNOWN_BREACHED_PASSWORDS.contains(password);
        } catch (Exception e) {
            // APP-01 §2.4 & K3: Servis hatasında kullanıcı mağdur edilmez, şifre kabul edilir ve olay loglanır.
            log.warn("Password breach check failed, accepting password as fail-safe: {}", e.getMessage());
            return false;
        }
    }

    public void setSimulateServiceError(boolean simulateServiceError) {
        this.simulateServiceError = simulateServiceError;
    }
}
