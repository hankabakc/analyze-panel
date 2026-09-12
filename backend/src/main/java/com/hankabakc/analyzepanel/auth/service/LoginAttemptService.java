package com.hankabakc.analyzepanel.auth.service;

import com.hankabakc.analyzepanel.auth.exception.AccountRateLimitException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginAttemptService: E-posta bazlı kaba kuvvet (Brute-Force) saldırılarını önlemek için
 * hesap düzeyinde kademeli gecikme (Exponential Backoff) uygular (ENG-11 §1.3 / T-056B).
 * 
 * <p>Mühendislik ve Güvenlik Standartları:</p>
 * <ul>
 *   <li>ENG-11 §1.2: Kullanıcı sayımı (User Enumeration) önlenir; var olan ve var olmayan
 *       e-postalar için aynı eşik ve aynı Retry-After uygulanır.</li>
 *   <li>ENG-11 §1.3: Eşik 5 ardışık hatalı denemedir. 5. hatadan sonra kademeli bekleme başlar:
 *       1. Kademe: 60 saniye (1 dakika)
 *       2. Kademe: 180 saniye (3 dakika)
 *       3. Kademe: 600 saniye (10 dakika - tavan)</li>
 *   <li>Hesap kilidi (lockout) yoktur; bekleme süresi bitince deneme hakkı açılır (T-008 kararı).</li>
 *   <li>Başarılı giriş sayaç ve cooldown durumunu sıfırlar.</li>
 *   <li>Bellek koruması: Süresi geçmiş ve aktif olmayan kayıtlar temizlenir; bellek saldırganın
 *       uydurduğu e-postalarla sınırsız büyümez.</li>
 * </ul>
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN = 5;
    private static final int TIER_1_SECONDS = 60;   // 1 dakika
    private static final int TIER_2_SECONDS = 180;  // 3 dakika
    private static final int TIER_3_SECONDS = 600;  // 10 dakika (tavan)
    private static final int MAX_ENTRIES_BEFORE_CLEANUP = 5000;
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Uygulamada sistem saati (UTC) kullanılır. */
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    /** Testler bekleme kademelerini gerçek süre beklemeden sınamak için saati verir (T-064). */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /**
     * checkRateLimit: Verilen e-postanın aktif bir bekleme (cooldown) süresi içinde
     * olup olmadığını denetler. Eğer bekleme süresi dolmamışsa AccountRateLimitException fırlatır.
     *
     * @param email Giriş yapılmak istenen e-posta adresi
     */
    public void checkRateLimit(String email) {
        String key = normalizeEmail(email);
        if (key.isBlank()) return;

        AttemptState state = attempts.get(key);
        if (state == null) return;

        Instant now = clock.instant();
        Instant cooldownUntil = state.cooldownUntil;
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            long remainingSeconds = Math.max(1, Duration.between(now, cooldownUntil).getSeconds());
            throw new AccountRateLimitException(
                    remainingSeconds,
                    "Çok fazla hatalı deneme. Lütfen " + remainingSeconds + " saniye sonra tekrar deneyin."
            );
        }
    }

    /**
     * recordFailure: Hatalı giriş denemesini kaydeder.
     * Ardışık hata sayısı 5 ve üzerine ulaştığında kademeli bekleme süresi belirler.
     *
     * @param email Hatalı giriş yapılan e-posta adresi
     */
    public void recordFailure(String email) {
        String key = normalizeEmail(email);
        if (key.isBlank()) return;

        pruneExpiredEntriesIfNeeded();

        Instant now = clock.instant();
        attempts.compute(key, (k, state) -> {
            if (state == null) {
                return new AttemptState(1, null, now);
            }

            int newFailures = state.consecutiveFailures + 1;
            Instant newCooldownUntil = null;

            if (newFailures >= MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN) {
                int cooldownSeconds = calculateCooldownSeconds(newFailures);
                newCooldownUntil = now.plusSeconds(cooldownSeconds);
            }

            return new AttemptState(newFailures, newCooldownUntil, now);
        });
    }

    /**
     * recordSuccess: Başarılı girişte sayacı ve bekleme süresini tamamen sıfırlar.
     *
     * @param email Giriş yapan kullanıcının e-posta adresi
     */
    public void recordSuccess(String email) {
        String key = normalizeEmail(email);
        if (key.isBlank()) return;

        attempts.remove(key);
    }

    /**
     * resetAll: Testler için tüm giriş sayaçlarını temizler.
     */
    public void resetAll() {
        attempts.clear();
    }

    /**
     * calculateCooldownSeconds: Hata sayısına göre kademeli bekleme süresini hesaplar.
     * 5. hata: 60 sn
     * 6. hata: 180 sn
     * 7+ hata: 600 sn (tavan)
     */
    private int calculateCooldownSeconds(int consecutiveFailures) {
        if (consecutiveFailures == 5) {
            return TIER_1_SECONDS;
        } else if (consecutiveFailures == 6) {
            return TIER_2_SECONDS;
        } else {
            return TIER_3_SECONDS;
        }
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    private void pruneExpiredEntriesIfNeeded() {
        if (attempts.size() > MAX_ENTRIES_BEFORE_CLEANUP) {
            Instant threshold = clock.instant().minus(ENTRY_TTL);
            attempts.entrySet().removeIf(entry -> {
                AttemptState s = entry.getValue();
                return s.lastAttemptTime.isBefore(threshold) &&
                        (s.cooldownUntil == null || s.cooldownUntil.isBefore(clock.instant()));
            });
        }
    }

    /**
     * AttemptState: Bellekte tutulan deneme durumu.
     */
    private static class AttemptState {
        final int consecutiveFailures;
        final Instant cooldownUntil;
        final Instant lastAttemptTime;

        AttemptState(int consecutiveFailures, Instant cooldownUntil, Instant lastAttemptTime) {
            this.consecutiveFailures = consecutiveFailures;
            this.cooldownUntil = cooldownUntil;
            this.lastAttemptTime = lastAttemptTime;
        }
    }
}
