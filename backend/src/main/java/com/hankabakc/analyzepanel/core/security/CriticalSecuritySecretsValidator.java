package com.hankabakc.analyzepanel.core.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * CriticalSecuritySecretsValidator: Uygulama açılışında kritik güvenlik sırlarının
 * ve ortam değişkenlerinin geçerliliğini denetler (ENG-10 §1, T-075 / T-079).
 *
 * Docker Compose tanımsız değişkenleri boş dize ("") olarak geçirdiğinde Spring yer tutucuları
 * hata vermeden çözebilir. Bu bileşen, açılış aşamasında (InitializingBean) anahtar uzunluklarını,
 * boşluk durumlarını ve prod ortamı kurallarını denetleyerek eksik veya zayıf anahtarlarla
 * uygulamanın çalışmasını engeller.
 */
@Component
public class CriticalSecuritySecretsValidator implements InitializingBean {

    private final String jwtSecretKey;
    private final String piiEncryptionKey;
    private final String initialAdminEmail;
    private final Environment environment;

    /**
     * Pure Java ve Dependency Injection kuralına uygun Constructor Injection.
     * Lombok YASAKTIR.
     */
    public CriticalSecuritySecretsValidator(
            @Value("${application.security.jwt.secret-key:}") String jwtSecretKey,
            @Value("${application.security.pii.encryption-key:}") String piiEncryptionKey,
            @Value("${application.security.initial-admin-email:}") String initialAdminEmail,
            Environment environment) {
        this.jwtSecretKey = jwtSecretKey;
        this.piiEncryptionKey = piiEncryptionKey;
        this.initialAdminEmail = initialAdminEmail;
        this.environment = environment;
    }

    /**
     * afterPropertiesSet: Bean başlatılır başlatılmaz tüm kritik anahtarları doğrular.
     */
    @Override
    public void afterPropertiesSet() {
        validateJwtSecretKey();
        validatePiiEncryptionKey();
        validateProdEnvironmentRequirements();
    }

    /**
     * JWT Gizli Anahtarını Denetler.
     * HS512 imza güvenliği için en az 64 karakter (512-bit) zorunludur.
     */
    private void validateJwtSecretKey() {
        if (jwtSecretKey == null || jwtSecretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'APPLICATION_SECURITY_JWT_SECRET_KEY' (application.security.jwt.secret-key) " +
                    "çevre değişkeni tanımlanmamış veya boş! HS512 için en az 64 karakter (512-bit) olmalıdır."
            );
        }

        if (jwtSecretKey.trim().length() < 64) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'APPLICATION_SECURITY_JWT_SECRET_KEY' (application.security.jwt.secret-key) " +
                    "çok kısa! HS512 için en az 64 karakter (512-bit) olmalıdır. Verilen uzunluk: " + jwtSecretKey.trim().length()
            );
        }
    }

    /**
     * PII Şifreleme Anahtarını Denetler.
     * AES-256 veritabanı şifrelemesi için anahtar TAM 32 byte (256-bit) olmak zorundadır.
     */
    private void validatePiiEncryptionKey() {
        if (piiEncryptionKey == null || piiEncryptionKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'PII_ENCRYPTION_KEY' (application.security.pii.encryption-key) " +
                    "çevre değişkeni tanımlanmamış veya boş! AES-256 için tam 32 karakter (256-bit) olmalıdır."
            );
        }

        int byteLength = piiEncryptionKey.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength != 32) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'PII_ENCRYPTION_KEY' (application.security.pii.encryption-key) " +
                    "geçersiz uzunlukta! AES-256 için tam 32 byte (256-bit) olmalıdır. Verilen byte uzunluğu: " + byteLength
            );
        }
    }

    /**
     * Canlı (prod) Ortamı Özel Gereksinimlerini Denetler.
     * Canlı ortamda tahmin edilebilir varsayılan yönetici e-postası (admin@admin.com) kullanılamaz.
     */
    private void validateProdEnvironmentRequirements() {
        boolean isProd = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
        if (isProd) {
            if (initialAdminEmail == null || initialAdminEmail.trim().isEmpty()) {
                throw new IllegalStateException(
                        "🚨 Kritik Güvenlik Hatası: Canlı (prod) ortamda 'INITIAL_ADMIN_EMAIL' " +
                        "(application.security.initial-admin-email) çevre değişkeni tanımlanmamış veya boş!"
                );
            }

            if ("admin@admin.com".equalsIgnoreCase(initialAdminEmail.trim())) {
                throw new IllegalStateException(
                        "🚨 Kritik Güvenlik Hatası: Canlı (prod) ortamda 'INITIAL_ADMIN_EMAIL' " +
                        "tahmin edilebilir varsayılan ('admin@admin.com') olamaz! Kuruma özel güvenli bir yönetici adresi tanımlanmalıdır."
                );
            }

            if (!initialAdminEmail.contains("@") || initialAdminEmail.trim().length() < 5) {
                throw new IllegalStateException(
                        "🚨 Kritik Güvenlik Hatası: 'INITIAL_ADMIN_EMAIL' geçerli bir e-posta formatında olmalıdır! Verilen: " + initialAdminEmail
                );
            }
        }
    }
}
