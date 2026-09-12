package com.hankabakc.analyzepanel.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * PasswordGenerator: Yöneticinin kullanıcı oluştururken veya ilk hesap açılışında
 * öğrenci/öğretmen için sesli söylenebilir ve elle okunabilir güvenli rastgele şifre üreten bileşendir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K4: Şifre en az 12 karakterdir. Görsel olarak birbiriyle karışabilen karakterler
 *       (0/O, 1/l/I) çıkarılmış güvenli alfabe kullanılır.</li>
 *   <li>ENG-11 §1.1: Kriptografik olarak güvenli SecureRandom kullanılır.</li>
 * </ul>
 */
@Component
public class PasswordGenerator {

    private static final String READABLE_CHARS = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int DEFAULT_LENGTH = 12;

    private final SecureRandom secureRandom;

    public PasswordGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * generateReadablePassword: 12 karakterlik, karışmayan harf ve rakamlardan oluşan okunabilir şifre üretir.
     *
     * @return 12 karakterlik rastgele güvenli şifre
     */
    public String generateReadablePassword() {
        return generateReadablePassword(DEFAULT_LENGTH);
    }

    /**
     * generateReadablePassword: Belirtilen uzunlukta okunabilir şifre üretir.
     *
     * @param length Üretilecek şifre uzunluğu (en az 12)
     * @return Rastgele şifre
     */
    public String generateReadablePassword(int length) {
        int actualLength = Math.max(length, DEFAULT_LENGTH);
        StringBuilder sb = new StringBuilder(actualLength);
        for (int i = 0; i < actualLength; i++) {
            int randomIndex = secureRandom.nextInt(READABLE_CHARS.length());
            sb.append(READABLE_CHARS.charAt(randomIndex));
        }
        return sb.toString();
    }
}
