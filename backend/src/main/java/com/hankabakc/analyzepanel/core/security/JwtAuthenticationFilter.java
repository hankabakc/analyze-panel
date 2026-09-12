package com.hankabakc.analyzepanel.core.security;

import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter: Gelen her HTTP isteğini yakalayan ve JWT doğrulamasını gerçekleştiren filtredir.
 * OncePerRequestFilter sınıfını genişleterek, her istek için sadece bir kez çalışması garanti edilir.
 * Bu filtre, stateless (durumsuz) güvenlik yapımızın temel taşıdır.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AppUserRepository userRepository;
    private final com.hankabakc.analyzepanel.auth.repository.BlacklistedTokenRepository blacklistedTokenRepository;

    public JwtAuthenticationFilter(JwtService jwtService, 
                                   UserDetailsService userDetailsService, 
                                   AppUserRepository userRepository,
                                   com.hankabakc.analyzepanel.auth.repository.BlacklistedTokenRepository blacklistedTokenRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    /**
     * doFilterInternal: Filtreleme mantığının ana gövdesidir.
     * 1. İstek başlığındaki 'Authorization' alanını kontrol eder.
     * 2. 'Bearer ' ile başlayan geçerli bir token varsa ayrıştırır.
     * 3. Token içinden kullanıcı adını (e-posta) çıkarır.
     * 4. Kullanıcı geçerliyse ve güvenlik bağlamı boşsa kullanıcıyı doğrular ve sisteme tanıtır.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = jwtService.extractTokenFromCookie(request, "access_token");

        // Kara Liste Kontrolü: Logout olmuş bir token ile giriş yapılamaz.
        if (jwt != null && blacklistedTokenRepository.existsByToken(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String userEmail;

        // Çerezde yoksa Authorization başlığına bak (Geriye dönük uyumluluk için)
        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        // Eğer hiç token bulunamazsa filtre zincirine devam et
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                
                if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                    java.util.Optional<com.hankabakc.analyzepanel.auth.entity.AppUser> userOpt = userRepository.findByEmail(userEmail);
                    if (userOpt.isPresent()) {
                        com.hankabakc.analyzepanel.auth.entity.AppUser user = userOpt.get();

                        // T-013B / B-46 & ENG-11 §2.4: Oturum İptal (Credentials Invalidation) Denetimi
                        // Şifre sıfırlama veya şifre değişikliği anından önce üretilmiş token'lar reddedilir.
                        if (user.getCredentialsInvalidatedAt() != null) {
                            java.util.Date issuedAt = jwtService.extractIssuedAt(jwt);
                            if (issuedAt != null) {
                                long iatEpochSecond = issuedAt.getTime() / 1000;
                                long invalidatedEpochSecond = user.getCredentialsInvalidatedAt().getEpochSecond();
                                if (iatEpochSecond < invalidatedEpochSecond) {
                                    // Token eski oturuma ait; kimlik doğrulaması yapılmaz (401).
                                    filterChain.doFilter(request, response);
                                    return;
                                }
                            }
                        }

                        request.setAttribute("userId", user.getId().toString());
                        request.setAttribute("role", user.getRole().name());
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token geçersiz veya süresi dolmuşsa sessizce devam et (Unauthorized hatası Controller seviyesinde yönetilir)
            log.warn("JWT Doğrulama Hatası: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}
