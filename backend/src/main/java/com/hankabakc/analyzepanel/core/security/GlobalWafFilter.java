package com.hankabakc.analyzepanel.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * GlobalWafFilter: Uygulama düzeyinde bir Güvenlik Duvarı (WAF) görevi görür.
 * OWASP 2026: Injection (A03) ve XSS saldırılarına karşı merkezi koruma sağlar.
 */
@Component
public class GlobalWafFilter extends OncePerRequestFilter {

    // SQL Injection Desenleri (Kaçış karakterleri düzeltildi)
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(DROP|DELETE|UPDATE|INSERT|TRUNCATE|UNION|SELECT|--|/\\*|\\*/|@@|OR\\s+1=1|1=1)",
            Pattern.CASE_INSENSITIVE
    );

    // XSS Desenleri (Kaçış karakterleri düzeltildi)
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script|alert\\(|onclick|onload|onerror|eval\\(|javascript:)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. URL ve Query Parametrelerini Tara
        String queryString = request.getQueryString();
        if (isMalicious(queryString) || isMalicious(request.getRequestURI())) {
            blockRequest(response, "Tehlikeli URL karakterleri tespit edildi.");
            return;
        }

        // 2. Tüm Request Parametrelerini Tara
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            for (String value : paramValues) {
                if (isMalicious(value)) {
                    blockRequest(response, "Payload engellendi: " + paramName);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isMalicious(String input) {
        if (input == null || input.isEmpty()) return false;
        return SQL_PATTERN.matcher(input).find() || XSS_PATTERN.matcher(input).find();
    }

    private void blockRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\": false, \"message\": \"GÜVENLİK ENGELİ: " + message + "\"}");
    }
}
