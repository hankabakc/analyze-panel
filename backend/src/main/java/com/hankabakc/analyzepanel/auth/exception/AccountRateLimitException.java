package com.hankabakc.analyzepanel.auth.exception;

/**
 * AccountRateLimitException: Hesap bazlı kademeli gecikme (Exponential Backoff) eşiği
 * aşıldığında fırlatılan özel çalışma zamanı istisnasıdır (ENG-11 §1.3 / T-056B).
 * 
 * <p>İstemciye iletilecek HTTP 429 Too Many Requests durum kodu ve Retry-After
 * başlığı için kalan bekleme saniyesini taşır.</p>
 */
public class AccountRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountRateLimitException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
