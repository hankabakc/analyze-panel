package com.hankabakc.analyzepanel.auth.service;

import com.hankabakc.analyzepanel.auth.exception.AccountRateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LoginAttemptServiceTest: Hesap bekletme kademelerini (60 sn → 3 dk → 10 dk tavan) saat enjeksiyonuyla,
 * gerçek süre beklemeden sınar (ENG-11 §1.3 / T-064). Veritabanına ve mevcut hesaplara dokunmaz.
 */
class LoginAttemptServiceTest {

    private static final String EMAIL = "kademe.test@example.com";

    private final TestClock clock = new TestClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    @DisplayName("4 hatalı deneme bekletmez; 5. hata 60 sn bekletir")
    void fifthFailureStartsFirstTier() {
        fail(4);
        assertDoesNotThrow(() -> service.checkRateLimit(EMAIL));

        fail(1);
        assertEquals(60, retryAfter());
    }

    @Test
    @DisplayName("Bekleme süresi dolunca deneme hakkı açılır; hesap kilitlenmez")
    void cooldownExpires() {
        fail(5);
        clock.advance(Duration.ofSeconds(59));
        assertEquals(1, retryAfter());

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> service.checkRateLimit(EMAIL));
    }

    @Test
    @DisplayName("Kademeler 60 sn → 180 sn → 600 sn ilerler; 600 sn tavandır")
    void tiersEscalateToCeiling() {
        fail(5);
        assertEquals(60, retryAfter());

        clock.advance(Duration.ofSeconds(60));
        fail(1);
        assertEquals(180, retryAfter());

        clock.advance(Duration.ofSeconds(180));
        fail(1);
        assertEquals(600, retryAfter());

        clock.advance(Duration.ofSeconds(600));
        fail(1);
        assertEquals(600, retryAfter(), "Tavan 10 dakika olmalı");
    }

    @Test
    @DisplayName("Başarılı giriş sayacı ve beklemeyi sıfırlar; e-posta büyük/küçük harf farkı aynı hesaptır")
    void successResetsCounter() {
        fail(5);
        service.recordSuccess(EMAIL.toUpperCase());
        assertDoesNotThrow(() -> service.checkRateLimit(EMAIL));

        fail(4);
        assertDoesNotThrow(() -> service.checkRateLimit(EMAIL));
    }

    private void fail(int count) {
        for (int i = 0; i < count; i++) {
            service.recordFailure(EMAIL);
        }
    }

    private long retryAfter() {
        return assertThrows(AccountRateLimitException.class, () -> service.checkRateLimit(EMAIL))
                .getRetryAfterSeconds();
    }

    /** Elle ilerletilen saat. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
