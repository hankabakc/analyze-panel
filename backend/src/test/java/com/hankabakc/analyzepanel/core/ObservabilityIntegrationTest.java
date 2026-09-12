package com.hankabakc.analyzepanel.core;

import com.hankabakc.analyzepanel.core.config.SentryConfig;
import com.hankabakc.analyzepanel.core.security.CorrelationIdFilter;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ObservabilityIntegrationTest: Sağlık uçları, Correlation ID istek izleme ve günlük/Sentry disiplinini doğrular (T-070 / S-027).
 *
 * <p>Kabul Kriterleri Kapsamı:</p>
 * <ul>
 *   <li>KK 1 & KK 3: /actuator/health ucu veritabanı bağlantısını kapsar (db.status: UP); sürüm/ortam detayı sızmaz (APP-01 §2.2).</li>
 *   <li>KK 2: Sağlık ucu dış ağa kapalıdır; yalnızca yerel loopback erişebilir, dış IP 403 Forbidden alır (IST-01 §3.2).</li>
 *   <li>KK 1: Diğer actuator uçları (/env, /beans vb.) kapalıdır.</li>
 *   <li>KK 4: İsteklere Correlation ID atanır, yanıtta X-Correlation-ID başlığı döner ve iki istek farklı kimlik alır.</li>
 *   <li>KK 5: Sentry beforeSend kancası kişisel veri (e-posta, ad, IP), parola ve token başlıklarını arındırır (APP-03 §4).</li>
 * </ul>
 */
@SpringBootTest
class ObservabilityIntegrationTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("KK 1, KK 3: Yerel loopback IP'den /actuator/health 200 döner, db bileşeni UP'tır ve iç sistem detayı sızmaz")
    void testLocalActuatorHealthReturnsUpAndDbComponentWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("KK 2: Dış ağ IP'sinden /actuator/health erişimi 403 Forbidden ile engellenir")
    void testRemoteActuatorHealthReturnsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.42");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("KK 1: Health harici Actuator uçları (/actuator/env, /actuator/beans) kapalıdır")
    void testDisabledActuatorEndpointsAreNotExposed() throws Exception {
        // /actuator/env kapalı olduğu için 404 döner
        mockMvc.perform(get("/actuator/env")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/actuator/beans")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("KK 4: X-Correlation-ID başlığı verilmediğinde filtre benzersiz bir UUID üretip yanıta ekler")
    void testCorrelationIdGeneratedWhenMissingAndReturnedInResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(correlationId);
        assertTrue(UUID_PATTERN.matcher(correlationId).matches(), 
                "Üretilen correlationId geçerli bir UUID olmalıdır: " + correlationId);
    }

    @Test
    @DisplayName("KK 4: İstemci geçerli bir X-Correlation-ID gönderdiğinde aynı kimlik korunup yanıta eklenir")
    void testCorrelationIdPreservedWhenProvidedByClient() throws Exception {
        String clientTraceId = "client-trace-id-998877";

        mockMvc.perform(get("/api/v1/site-content")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, clientTraceId))
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, clientTraceId));
    }

    @Test
    @DisplayName("KK 4: İki ardışık HTTP isteği birbirinden farklı iki Correlation ID alır")
    void testTwoRequestsGetDifferentCorrelationIds() throws Exception {
        MvcResult result1 = mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andReturn();

        MvcResult result2 = mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andReturn();

        String id1 = result1.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String id2 = result2.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "İki ayrı istek iki ayrı Correlation ID almalıdır.");
    }

    @Test
    @DisplayName("KK 5: Sentry beforeSend kancası e-posta, kullanıcı adı, IP, parola ve token başlıklarını arındırır")
    void testSentryBeforeSendSanitizationRemovesPiiAndSensitiveHeaders() {
        SentryEvent event = new SentryEvent();

        // 1. Kullanıcı PII Bilgileri
        User user = new User();
        user.setEmail("ogrenci@analyzepanel.local");
        user.setUsername("canberk.yildiz");
        user.setIpAddress("192.168.1.105");
        event.setUser(user);

        // 2. HTTP Başlıkları ve Çerezler
        Request request = new Request();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");
        headers.put("Cookie", "session=super_secret_cookie_123");
        headers.put("Set-Cookie", "jwt=sensitive_token");
        headers.put("X-XSRF-TOKEN", "xsrf_token_value");
        headers.put("User-Agent", "Mozilla/5.0 TestBrowser");
        headers.put("Accept", "application/json");
        request.setHeaders(headers);
        request.setCookies("secret_cookie_payload");
        event.setRequest(request);

        // 3. MDC'de Correlation ID varken
        String testCorrId = "corr-test-id-554433";
        MDC.put(CorrelationIdFilter.MDC_KEY, testCorrId);

        try {
            // Arındırma fonksiyonunu çalıştır
            SentryEvent sanitized = SentryConfig.sanitizeEvent(event);

            assertNotNull(sanitized);

            // Kullanıcı PII alanlarının arındırıldığı doğrulanır
            assertNotNull(sanitized.getUser());
            assertNull(sanitized.getUser().getEmail(), "Kullanıcı e-postası arındırılmalıdır (null olmalı)");
            assertNull(sanitized.getUser().getUsername(), "Kullanıcı adı arındırılmalıdır (null olmalı)");
            assertNull(sanitized.getUser().getIpAddress(), "Kullanıcı IP adresi arındırılmalıdır (null olmalı)");

            // İstek başlıkları ve çerezlerin arındırıldığı doğrulanır
            assertNotNull(sanitized.getRequest());
            assertNull(sanitized.getRequest().getCookies(), "Çerezler tamamen temizlenmelidir (null olmalı)");

            Map<String, String> sanitizedHeaders = sanitized.getRequest().getHeaders();
            assertNotNull(sanitizedHeaders);
            assertFalse(sanitizedHeaders.containsKey("Authorization"), "Authorization başlığı kaldırılmalıdır");
            assertFalse(sanitizedHeaders.containsKey("Cookie"), "Cookie başlığı kaldırılmalıdır");
            assertFalse(sanitizedHeaders.containsKey("Set-Cookie"), "Set-Cookie başlığı kaldırılmalıdır");
            assertFalse(sanitizedHeaders.containsKey("X-XSRF-TOKEN"), "X-XSRF-TOKEN başlığı kaldırılmalıdır");

            // Güvenli başlıklar korunur
            assertEquals("Mozilla/5.0 TestBrowser", sanitizedHeaders.get("User-Agent"));
            assertEquals("application/json", sanitizedHeaders.get("Accept"));

            // Correlation ID etiket olarak eklenir
            assertEquals(testCorrId, sanitized.getTag("correlation_id"), "Correlation ID sentry etiketine eklenmelidir");
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
