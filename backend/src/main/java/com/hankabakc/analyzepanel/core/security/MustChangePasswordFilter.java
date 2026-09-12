package com.hankabakc.analyzepanel.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * MustChangePasswordFilter: İlk girişte veya yönetici tarafından şifresi sıfırlanmış kullanıcıların
 * (must_change_password = true) şifrelerini değiştirmeden korumalı kaynaklara erişmesini engelleyen güvenlik filtresidir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K5 & IST-01 §3.2: Şifre değiştirme zorunluluğu istemciye bırakılmaz, sunucu tarafında
 *       izin listesi dışındaki tüm isteklere 403 Forbidden dönerek zorlanır.</li>
 *   <li>İzin Listesi: /api/v1/auth/password/change, /api/v1/auth/logout, /api/v1/auth/me.</li>
 * </ul>
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/password/change",
            "/api/v1/auth/logout",
            "/api/v1/auth/me"
    );

    public MustChangePasswordFilter(AppUserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Kimliği doğrulanmış kullanıcıyı kontrol et
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();

            // İzin listesinde değilse veritabanından mustChangePassword bayrağını denetle
            if (!isAllowedPath(path)) {
                boolean mustChange = userRepository.findByEmail(email)
                        .map(user -> user.isMustChangePassword())
                        .orElse(false);

                if (mustChange) {
                    // K5: İzin listesi dışındaki tüm isteklere 403 Forbidden ve standart ApiResponse döner
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    ApiResponse<Void> errorResponse = ApiResponse.error("Devam etmek için şifrenizi değiştirmeniz gerekmektedir.");
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedPath(String path) {
        if (path == null) return false;
        return ALLOWED_PATHS.stream().anyMatch(path::startsWith);
    }
}
