package com.hankabakc.analyzepanel.core.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RateLimitExceededException: Hız sınırı ve kademeli gecikme (cooldown) aşıldığında
 * 429 Too Many Requests ve Retry-After başlığı dönen istemci istisnasıdır (ENG-07 §2, T-014).
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>ENG-07 §2: Hız sınırı ihlallerinde HTTP 429 Too Many Requests ve Retry-After başlığı zorunludur.</li>
 *   <li>ENG-03 §2: Bu istisna 4xx istemci hatasıdır; Sentry veya hata loglarını kirletmez.</li>
 * </ul>
 */
public class RateLimitExceededException extends ResponseStatusException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long remainingSeconds, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = remainingSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return headers;
    }
}
