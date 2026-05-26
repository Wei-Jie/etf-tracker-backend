package com.etftracker.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    @Value("${app.api-key}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 放行 OPTIONS 請求 (CORS 預檢)
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 放行輕量級免驗證端點：健康檢查 (GET /health) 與 今日 AI 晨報 (GET /news/briefing)
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if (path.endsWith("/api/v1/health") || path.endsWith("/api/v1/health/")
                || path.endsWith("/api/v1/news/briefing") || path.endsWith("/api/v1/news/briefing/")) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 擷取 Header 中的 API Key
        String reqApiKey = request.getHeader(API_KEY_HEADER);

        // 驗證 API Key
        if (expectedApiKey.equals(reqApiKey)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid API Key");
        }
    }
}
