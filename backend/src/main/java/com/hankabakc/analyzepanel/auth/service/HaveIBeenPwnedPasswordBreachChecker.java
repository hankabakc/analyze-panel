package com.hankabakc.analyzepanel.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.UnaryOperator;

/**
 * HaveIBeenPwnedPasswordBreachChecker: Sızıntı denetiminin canlı ortamdaki gerçek adaptörü (T-069).
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.6: Sahte adaptör (`MockPasswordBreachChecker`) üretim profilinde etkinleşemez;
 *       bu sınıf yalnızca `prod` profilinde devreye girer. İş katmanı (`AuthService`) değişmez.</li>
 *   <li>APP-01 §2.4: Servis yanıt vermezse şifre KABUL EDİLİR (fail-open) ve olay loglanır —
 *       dış servisin çökmesi kullanıcının şifresini değiştirmesini engellemez.</li>
 *   <li>APP-03 §4: Şifre veya tam özeti dışarı çıkmaz. k-anonymity: yalnızca SHA-1 özetinin
 *       ilk 5 hanesi gönderilir, eşleşme yanıtın içinde yerel olarak aranır.</li>
 * </ul>
 *
 * <p>Zaman aşımları açıkça tanımlıdır: bağlantı 2 sn, yanıt 3 sn. Tanımsız zaman aşımı, dış servis
 * asılı kaldığında istek thread'ini kilitler.</p>
 */
@Component
@Profile("prod")
public class HaveIBeenPwnedPasswordBreachChecker implements PasswordBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(HaveIBeenPwnedPasswordBreachChecker.class);

    private static final String RANGE_URL = "https://api.pwnedpasswords.com/range/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    /** Özet önekini alıp servisin yanıtını döndürür; testte ağa çıkmadan değiştirilir (IST-08 §1.1). */
    private final UnaryOperator<String> rangeFetcher;

    public HaveIBeenPwnedPasswordBreachChecker() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.rangeFetcher = prefix -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RANGE_URL + prefix))
                    // Yanıt boyutundan önek tahmin edilmesin diye servis dolgu satırı ekler.
                    .header("Add-Padding", "true")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Beklenmeyen yanıt kodu: " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Sızıntı denetimi kesildi", e);
            } catch (Exception e) {
                throw new IllegalStateException("Sızıntı denetim servisine ulaşılamadı", e);
            }
        };
    }

    /** Testler için: ağ yerine verilen işlevi kullanır. */
    HaveIBeenPwnedPasswordBreachChecker(UnaryOperator<String> rangeFetcher) {
        this.rangeFetcher = rangeFetcher;
    }

    @Override
    public boolean isBreached(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        try {
            String hash = sha1Hex(password);
            String prefix = hash.substring(0, 5);
            String suffix = hash.substring(5);

            for (String line : rangeFetcher.apply(prefix).split("\\R")) {
                int separator = line.indexOf(':');
                String candidate = separator < 0 ? line.trim() : line.substring(0, separator).trim();
                if (candidate.equalsIgnoreCase(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // APP-01 §2.4: Kullanıcı dış servisin arızasından dolayı engellenmez.
            log.warn("Şifre sızıntı denetimi yapılamadı, şifre kabul ediliyor: {}", e.getMessage());
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 bulunamadı", e);
        }
    }
}
