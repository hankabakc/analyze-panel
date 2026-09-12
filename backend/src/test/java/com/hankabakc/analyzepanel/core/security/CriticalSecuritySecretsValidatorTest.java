package com.hankabakc.analyzepanel.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CriticalSecuritySecretsValidatorTest: Kritik güvenlik sırları ve ortam değişkenlerinin
 * açılış doğrulamalarını test eder (ENG-10 §1, T-075 / T-079).
 */
class CriticalSecuritySecretsValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CriticalSecuritySecretsValidator.class);

    private static final String VALID_JWT_SECRET = "en-az-64-karakter-uzunlugunda-guvenli-jwt-anahtari-hs512-icin-nukleer-zirh-1234567890";
    private static final String VALID_PII_KEY = "tam_32_karakter_uzunluk_key_1234"; // exactly 32 chars / 32 bytes
    private static final String VALID_PROD_EMAIL = "guvenli.yonetici@kurum.com";

    @Test
    @DisplayName("JWT Secret Key boş olduğunda açılış durdurulmalı ve hata mesajında değişken adı belirtilmeli")
    void testFailsWhenJwtSecretKeyIsEmpty() {
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=",
                        "application.security.pii.encryption-key=" + VALID_PII_KEY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("APPLICATION_SECURITY_JWT_SECRET_KEY")
                            .hasMessageContaining("tanımlanmamış veya boş");
                });
    }

    @Test
    @DisplayName("JWT Secret Key 64 karakterden kısa olduğunda açılış durdurulmalı")
    void testFailsWhenJwtSecretKeyIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=kisa-anahtar-123",
                        "application.security.pii.encryption-key=" + VALID_PII_KEY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("APPLICATION_SECURITY_JWT_SECRET_KEY")
                            .hasMessageContaining("çok kısa! HS512 için en az 64 karakter");
                });
    }

    @Test
    @DisplayName("T-076: JWT Secret Key 32-63 karakter arasında olduğunda HS512 için açılış durdurulmalı")
    void testFailsWhenJwtSecretKeyIsBetween32And63Chars() {
        // 48 karakterlik anahtar (32'den büyük ama HS512 için yetersiz < 64)
        String mediumSecret = "bu-anahtar-tam-48-karakter-uzunlugundadir-guvenli123";
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=" + mediumSecret,
                        "application.security.pii.encryption-key=" + VALID_PII_KEY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("APPLICATION_SECURITY_JWT_SECRET_KEY")
                            .hasMessageContaining("HS512 için en az 64 karakter (512-bit) olmalıdır")
                            .hasMessageContaining("Verilen uzunluk: " + mediumSecret.length());
                });
    }

    @Test
    @DisplayName("PII Encryption Key boş olduğunda açılış durdurulmalı")
    void testFailsWhenPiiEncryptionKeyIsEmpty() {
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("PII_ENCRYPTION_KEY")
                            .hasMessageContaining("tanımlanmamış veya boş");
                });
    }

    @Test
    @DisplayName("PII Encryption Key 32 byte olmadığında açılış durdurulmalı")
    void testFailsWhenPiiEncryptionKeyIsNot32Bytes() {
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key=yanlis_uzunluk_key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("PII_ENCRYPTION_KEY")
                            .hasMessageContaining("AES-256 için tam 32 byte (256-bit) olmalıdır");
                });
    }

    @Test
    @DisplayName("Canlı (prod) profilinde INITIAL_ADMIN_EMAIL boş olduğunda açılış durdurulmalı")
    void testFailsInProdWhenInitialAdminEmailIsEmpty() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key=" + VALID_PII_KEY,
                        "application.security.initial-admin-email="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("INITIAL_ADMIN_EMAIL")
                            .hasMessageContaining("tanımlanmamış veya boş");
                });
    }

    @Test
    @DisplayName("Canlı (prod) profilinde INITIAL_ADMIN_EMAIL tahmin edilebilir varsayılan (admin@admin.com) olduğunda açılış durdurulmalı")
    void testFailsInProdWhenInitialAdminEmailIsDefaultAdmin() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key=" + VALID_PII_KEY,
                        "application.security.initial-admin-email=admin@admin.com"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("INITIAL_ADMIN_EMAIL")
                            .hasMessageContaining("tahmin edilebilir varsayılan ('admin@admin.com') olamaz");
                });
    }

    @Test
    @DisplayName("Canlı (prod) profilinde geçerli değerler sağlandığında bağlam başarıyla açılmalı")
    void testSucceedsInProdWithValidConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key=" + VALID_PII_KEY,
                        "application.security.initial-admin-email=" + VALID_PROD_EMAIL
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CriticalSecuritySecretsValidator.class);
                });
    }

    @Test
    @DisplayName("Geliştirme profilinde geçerli yerel anahtarlarla bağlam başarıyla açılmalı")
    void testSucceedsInDevWithValidConfiguration() {
        contextRunner
                .withPropertyValues(
                        "application.security.jwt.secret-key=" + VALID_JWT_SECRET,
                        "application.security.pii.encryption-key=" + VALID_PII_KEY,
                        "application.security.initial-admin-email=admin@admin.com"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CriticalSecuritySecretsValidator.class);
                });
    }
}
