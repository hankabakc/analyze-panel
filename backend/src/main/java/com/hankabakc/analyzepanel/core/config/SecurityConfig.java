package com.hankabakc.analyzepanel.core.config;

import com.hankabakc.analyzepanel.core.security.CorrelationIdFilter;
import com.hankabakc.analyzepanel.core.security.GlobalWafFilter;
import com.hankabakc.analyzepanel.core.security.JwtAuthenticationFilter;
import com.hankabakc.analyzepanel.core.security.MustChangePasswordFilter;
import com.hankabakc.analyzepanel.core.security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig: Spring Security mimarisini, CORS, CSRF, oturum yönetimi ve yetkilendirme kurallarını yapılandırır.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K1, K5: /api/v1/auth/login CSRF muafiyetine eklendi; MustChangePasswordFilter ile zorunlu şifre kapısı bağlandı.</li>
 *   <li>T-070 / S-027: CorrelationIdFilter eklendi, /actuator/health ucu yalnızca yerel loopback erişimine bağlandı (IST-01 §3.2).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final GlobalWafFilter globalWafFilter;
    private final MustChangePasswordFilter mustChangePasswordFilter;

    // T-069: İzinli kaynaklar ortamdan gelir; canlıda yalnızca kendi alan adı tanımlıdır (S-027).
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    // T-069: CSRF çerezi de oturum çerezleriyle aynı bayrakları taşır; canlıda yalnızca HTTPS ile gider.
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    public SecurityConfig(CorrelationIdFilter correlationIdFilter,
                          JwtAuthenticationFilter jwtAuthFilter, 
                          RateLimitingFilter rateLimitingFilter,
                          GlobalWafFilter globalWafFilter,
                          MustChangePasswordFilter mustChangePasswordFilter) {
        this.correlationIdFilter = correlationIdFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.globalWafFilter = globalWafFilter;
        this.mustChangePasswordFilter = mustChangePasswordFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(cookieSecure).sameSite(cookieSameSite));

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // null: CSRF token'ı isteğin başında bir kez yüklenir (eager). Varsayılan
        // "deferred" modda token yalnızca birileri ona dokununca üretiliyor ve her
        // dokunuşta yeni bir çerez yazılıyordu; arayüz çerezi okuyup başlığa koyarken
        // sunucu onu çoktan değiştirmiş oluyor, istek 403 dönüyordu.
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/api/v1/auth/login")
            )
            .headers(headers -> {
                headers.frameOptions(frame -> frame.deny());
                headers.xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK));
                headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"));
                headers.contentTypeOptions(org.springframework.security.config.Customizer.withDefaults());
                headers.referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"));
                headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000));
            })
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Hata sayfasına yönlenen istekler (ERROR dispatch) güvenlikten muaf.
                // Aksi halde istek daha filtre zincirine girmeden patladığında -örneğin
                // yüklenen dosya boyut sınırını aştığında- kimlik doğrulanamamış sayılıp
                // asıl hata "Oturum süresi doldu" (401) diye maskeleniyordu.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                // T-070 / IST-01 §3.2: Sağlık ucu yalnızca yerel loopback erişimine (127.0.0.1, ::1) açıktır.
                // Dış ağdan gelen istekler 403 Forbidden alır.
                .requestMatchers("/actuator/health", "/actuator/health/**").access((authentication, context) -> {
                    String remoteAddr = context.getRequest().getRemoteAddr();
                    boolean isLocal = "127.0.0.1".equals(remoteAddr)
                            || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                            || "::1".equals(remoteAddr);
                    return new AuthorizationDecision(isLocal);
                })
                .requestMatchers("/api/v1/auth/**").permitAll()
                // T-063A / S-026: Karşılama sitesinin içeriği herkese açık okunur;
                // alan listesi (/fields) ve yazma (PUT) kimlik ister, yönetici denetimi uçta yapılır.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/site-content").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("MANAGER")
                .anyRequest().authenticated()
            )
            // Kimliği doğrulanmamış istekler 401 döner, 403 değil.
            // Actuator sağlık uçlarına dış IP'den gelen erişim reddedildiğinde 403 döner (IST-01 §3.2).
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                if (request.getRequestURI().startsWith("/actuator")) {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"success\":false,\"message\":\"Erişim engellendi: Sağlık uçları yalnızca yerel ağa açıktır.\"}");
                    return;
                }
                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Oturum bulunamadı veya süresi doldu.\"}");
            }))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(correlationIdFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterBefore(globalWafFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Retry-After", "X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
