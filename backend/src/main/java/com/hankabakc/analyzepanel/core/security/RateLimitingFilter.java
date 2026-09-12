package com.hankabakc.analyzepanel.core.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitingFilter: IP ve kullanıcı bazlı hız sınırlandırması (Rate Limiting) uygular.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>ENG-07 §3.1: Açık kimlik doğrulama uç noktaları (/api/v1/auth/login)
 *       için IP başına dakikada maksimum 5 istek sınırı uygulanır.</li>
 *   <li>Diğer API uç noktaları için genel sınır dakikada 60 istektir.</li>
 *   <li>429 Too Many Requests yanıtında standart Retry-After başlığı döner.</li>
 * </ul>
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean isAuthEndpoint = "/api/v1/auth/login".equals(uri);

        // T-056B: IP yalnızca bağlantı adresinden okunur. X-Forwarded-For istemcinin uydurabildiği bir başlıktır;
        // her istekte farklı değer gönderilerek sınır atlatılıyordu. Ters vekil arkasına çıkılırsa güvenilir
        // vekil ayarı yayın işidir (S-001).
        String ip = request.getRemoteAddr();
        String identifier;
        Bucket bucket;

        if (isAuthEndpoint) {
            // Açık auth uç noktaları için katı hız sınırı: IP başına dakikada 5 istek
            identifier = "AUTH_IP:" + ip;
            bucket = buckets.computeIfAbsent(identifier, k -> createAuthBucket());
        } else {
            // Genel uç noktalar için kullanıcı veya IP bazlı sınır: dakikada 60 istek
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                identifier = "USER:" + auth.getName();
            } else {
                identifier = "IP:" + ip;
            }
            bucket = buckets.computeIfAbsent(identifier, k -> createGeneralBucket());
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Hız sınırını aştınız. Lütfen bir dakika bekleyin.\"}");
        }
    }

    /**
     * createAuthBucket: Açık auth uçları için dakikada 5 istek sınırı.
     */
    private Bucket createAuthBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * createGeneralBucket: Standart uçlar için dakikada 60 istek sınırı.
     */
    private Bucket createGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1))))
                .build();
    }
}
