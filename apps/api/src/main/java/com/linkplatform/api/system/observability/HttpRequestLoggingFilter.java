package com.linkplatform.api.system.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final int MAX_DETAIL_LENGTH = 120;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info(
                    "http_request method={} path={} status={} outcome={} duration_ms={} auth_mode={} remote={} operation={} detail={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    requestOutcome(response.getStatus()),
                    durationMillis,
                    authMode(request),
                    anonymizedRemote(request.getRemoteAddr()),
                    operationMarker(request),
                    safeDetail(request));
        }
    }

    private String authMode(HttpServletRequest request) {
        boolean apiKeyPresent = headerPresent(request.getHeader(API_KEY_HEADER));
        boolean authorizationPresent = headerPresent(request.getHeader(AUTHORIZATION_HEADER));
        if (apiKeyPresent && authorizationPresent) {
            return "both";
        }
        if (authorizationPresent) {
            return "bearer";
        }
        if (apiKeyPresent) {
            return "api-key";
        }
        return "absent";
    }

    private boolean headerPresent(String value) {
        return value != null && !value.isBlank();
    }

    private String requestOutcome(int status) {
        if (status >= 500) {
            return "server_error";
        }
        if (status >= 400) {
            return "client_error";
        }
        if (status >= 300) {
            return "redirect";
        }
        return "success";
    }

    private String anonymizedRemote(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        int lastSeparator = Math.max(remoteAddress.lastIndexOf('.'), remoteAddress.lastIndexOf(':'));
        if (lastSeparator <= 0) {
            return "hashed";
        }
        return remoteAddress.substring(0, lastSeparator) + ".x";
    }

    private String operationMarker(HttpServletRequest request) {
        Object attribute = request.getAttribute("operatorOperation");
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            return switch (path) {
                case "/api/v1/analytics/pipeline/pause" -> "analytics_pipeline_pause";
                case "/api/v1/analytics/pipeline/resume" -> "analytics_pipeline_resume";
                case "/api/v1/analytics/pipeline/force-tick" -> "analytics_pipeline_force_tick";
                case "/api/v1/analytics/pipeline/parked/drain" -> "analytics_pipeline_drain";
                case "/api/v1/lifecycle/pipeline/pause" -> "lifecycle_pipeline_pause";
                case "/api/v1/lifecycle/pipeline/resume" -> "lifecycle_pipeline_resume";
                case "/api/v1/lifecycle/pipeline/force-tick" -> "lifecycle_pipeline_force_tick";
                case "/api/v1/lifecycle/pipeline/parked/drain" -> "lifecycle_pipeline_drain";
                default -> "none";
            };
        }
        return "none";
    }

    private String safeDetail(HttpServletRequest request) {
        Object attribute = request.getAttribute("operatorDetail");
        if (!(attribute instanceof String value) || value.isBlank()) {
            return "none";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_DETAIL_LENGTH ? trimmed : trimmed.substring(0, MAX_DETAIL_LENGTH);
    }
}
