package com.hankabakc.analyzepanel.core.config;

import com.hankabakc.analyzepanel.core.security.CorrelationIdFilter;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * SentryConfig: Sentry SDK'sını uygulama açılışında başlatır ve PII arındırma disiplinini uygular.
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-070 / S-027: beforeSend kancası ile kişisel veri (e-posta, kullanıcı adı, IP) ve hassas başlıklar (Authorization, Cookie) arındırılır (APP-03 §4, ENG-10 §1).</li>
 *   <li>Correlation ID Sentry olaylarına etiket (tag) olarak eklenir.</li>
 *   <li>SLF4J Logger disiplinine geçilmiştir.</li>
 * </ul>
 */
@Configuration
public class SentryConfig {

    private static final Logger log = LoggerFactory.getLogger(SentryConfig.class);

    @Value("${sentry.dsn:}")
    private String dsn;

    @Value("${sentry.environment:local}")
    private String environment;

    @Value("${sentry.send-default-pii:false}")
    private boolean sendDefaultPii;

    @PostConstruct
    public void init() {
        if (dsn == null || dsn.isBlank()) {
            log.info("[SENTRY] DSN tanımlı değil, hata izleme kapalı.");
            return;
        }
        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setEnvironment(environment);
            options.setSendDefaultPii(sendDefaultPii);
            options.setBeforeSend((event, hint) -> sanitizeEvent(event));
        });
        log.info("[SENTRY] Hata izleme aktif (environment={}).", environment);
    }

    /**
     * Sentry olaylarından kişisel verileri (PII) ve kimlik doğrulama belirteçlerini arındırır.
     * Bu metot birim ve entegrasyon testlerinde de doğrudan doğrulanabilir.
     *
     * @param event Ham Sentry olayı
     * @return Arındırılmış Sentry olayı
     */
    public static SentryEvent sanitizeEvent(SentryEvent event) {
        if (event == null) {
            return null;
        }

        // 1. Kullanıcı PII Arındırması (APP-03 §4)
        if (event.getUser() != null) {
            event.getUser().setEmail(null);
            event.getUser().setUsername(null);
            event.getUser().setIpAddress(null);
        }

        // 2. HTTP İstek Başlıkları ve Çerezleri Arındırma (Token, Şifre, Cookie vb.)
        if (event.getRequest() != null) {
            event.getRequest().setCookies(null);
            Map<String, String> headers = event.getRequest().getHeaders();
            if (headers != null) {
                headers.keySet().removeIf(header -> 
                    header.equalsIgnoreCase("Authorization") ||
                    header.equalsIgnoreCase("Cookie") ||
                    header.equalsIgnoreCase("Set-Cookie") ||
                    header.equalsIgnoreCase("X-XSRF-TOKEN") ||
                    header.toLowerCase().contains("token") ||
                    header.toLowerCase().contains("secret") ||
                    header.toLowerCase().contains("password")
                );
            }
        }

        // 3. Correlation ID Etiketleme (İstek izleme eşleşmesi)
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            event.setTag("correlation_id", correlationId);
        }

        return event;
    }
}
