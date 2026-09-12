package com.hankabakc.analyzepanel.membership.service;

import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * GeneratedEmailFactory: Kullanıcının ad soyad bilgisinden sistem içi benzersiz e-posta adresi üretir.
 * 
 * <p>Tasarım İlkeleri (T-004 / K2):</p>
 * <ul>
 *   <li>1. Ad soyad ASCII'ye çevrilir (ş->s, ı->i, İ->i, ğ->g, ü->u, ö->o, ç->c), küçük harfe indirilir.</li>
 *   <li>2. Boşluklar '.' olur, [a-z0-9.] dışındaki karakterler atılır, ardışık noktalar teke iner, uç noktalar kırpılır.</li>
 *   <li>3. Temizlik sonrası boş kalırsa 'kullanici' taban adı kullanılır.</li>
 *   <li>4. slug@domain denenir; kayıtlıysa slug2@domain, slug3@domain şeklinde ilk boş e-posta seçilir.</li>
 * </ul>
 */
@Component
public class GeneratedEmailFactory {

    private final AppUserRepository userRepository;
    private final String domain;

    public GeneratedEmailFactory(
            AppUserRepository userRepository,
            @Value("${application.security.generated-email-domain:analyzepanel.local}") String domain
    ) {
        this.userRepository = userRepository;
        this.domain = (domain != null && !domain.isBlank()) ? domain.trim() : "analyzepanel.local";
    }

    /**
     * generateUniqueEmail: Verilen ad soyad için çakışmasız sistem e-postası üretir.
     * 
     * @param fullName Kullanıcının adı ve soyadı
     * @return Sistemde benzersiz, şifreli aramaya uygun e-posta adresi
     */
    public String generateUniqueEmail(String fullName) {
        String slug = createSlug(fullName);

        // 1. İlk aday: slug@domain
        String firstCandidate = slug + "@" + domain;
        if (userRepository.findByEmail(firstCandidate).isEmpty()) {
            return firstCandidate;
        }

        // 2. Çakışma döngüsü: slug2@domain, slug3@domain...
        int counter = 2;
        while (true) {
            String candidate = slug + counter + "@" + domain;
            if (userRepository.findByEmail(candidate).isEmpty()) {
                return candidate;
            }
            counter++;
        }
    }

    /**
     * createSlug: Ad soyadı kurallara uygun slug formatına dönüştürür.
     */
    public static String createSlug(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "kullanici";
        }

        // 1. Türkçe harf dönüşümleri (Büyük/Küçük harfler)
        String s = fullName
                .replace("İ", "i")
                .replace("I", "i")
                .replace("ı", "i")
                .replace("Ş", "s")
                .replace("ş", "s")
                .replace("Ğ", "g")
                .replace("ğ", "g")
                .replace("Ü", "u")
                .replace("ü", "u")
                .replace("Ö", "o")
                .replace("ö", "o")
                .replace("Ç", "c")
                .replace("ç", "c")
                .toLowerCase(Locale.ENGLISH);

        // 2. Boşlukları nokta yap ve [a-z0-9.] haricindekileri temizle
        s = s.replace(" ", ".");
        s = s.replaceAll("[^a-z0-9.]", "");

        // 3. Art arda gelen noktaları teke indir
        s = s.replaceAll("\\.+", ".");

        // 4. Baştaki ve sondaki noktaları kırp
        if (s.startsWith(".")) {
            s = s.substring(1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }

        // 5. Boş kaldıysa güvenli varsayılan
        if (s.isBlank()) {
            return "kullanici";
        }

        return s;
    }
}
