package com.hankabakc.analyzepanel.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * EncryptionUtils: AES-256 algoritması ile veritabanı seviyesinde şifreleme sağlar.
 */
@Component
public class EncryptionUtils {

    private static String encryptionKey;

    @Value("${application.security.pii.encryption-key:}")
    public void setEncryptionKey(String key) {
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalStateException(
                    "🚨 Kritik Güvenlik Hatası: 'PII_ENCRYPTION_KEY' (application.security.pii.encryption-key) " +
                    "tanımlanmamış, boş veya geçersiz uzunlukta! AES-256 için tam 32 byte (256-bit) olmalıdır. Verilen: " +
                    (key == null ? "null" : key.length() + " karakter")
            );
        }
        EncryptionUtils.encryptionKey = key;
    }

    public static String encrypt(String attribute) {
        if (attribute == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Veri şifrelenirken hata oluştu.", e);
        }
    }

    public static String decrypt(String dbData) {
        if (dbData == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Şifre çözülemiyorsa veri zaten şifresiz olabilir veya anahtar yanlıştır
            return dbData;
        }
    }
}
