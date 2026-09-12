package com.hankabakc.analyzepanel.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CorrelationIdFilter: Her gelen HTTP isteğine benzersiz bir izleme kimliği (Correlation ID) atar.
 * 
 * Amaç:
 * 1. Dağıtık veya yerel sistemlerde istemci-sunucu arasındaki istek akışını uçtan uca izlemek (S-027 / T-070).
 * 2. Log kayıtlarında '%X{correlationId}' deseniyle günlükleri eşleştirmek.
 * 3. Virtual Thread (Loom) ortamlarında MDC kirliliğini önlemek amacıyla 'finally' bloğunda MDC temizliği yapmak (ENG-05).
 * 4. 'X-Correlation-ID' başlığı ile istemciye yanıt içinde aynı kimliği dönmek.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    // Correlation ID format doğrulama deseni: Yalnızca alfanümerik, tire ve alt çizgi (maksimum 64 karakter)
    private static final Pattern VALID_CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    /**
     * Filtreleme mantığı:
     * - İstemciden geçerli bir X-Correlation-ID gelmişse onu kullanır.
     * - Gelmemişse veya geçersiz karakter içeriyorsa yeni bir UUID üretir.
     * - Yanıt başlığına ekler, MDC'ye kaydeder ve zincir sonunda MDC'yi temizler.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String incomingCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId;

        // İstemciden gelen başlığı doğrula (Header injection veya zararlı içerik riskine karşı)
        if (incomingCorrelationId != null && !incomingCorrelationId.isBlank() 
                && VALID_CORRELATION_ID_PATTERN.matcher(incomingCorrelationId.trim()).matches()) {
            correlationId = incomingCorrelationId.trim();
        } else {
            correlationId = UUID.randomUUID().toString();
        }

        // SLF4J MDC'ye kaydet
        MDC.put(MDC_KEY, correlationId);

        // İstemciye dönecek yanıta ekle
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Virtual Thread ve thread pool kirlenmesini engellemek için temizlik şarttır
            MDC.remove(MDC_KEY);
        }
    }
}
