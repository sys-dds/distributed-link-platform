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
                    "http_request method={} path={} status={} duration_ms={} api_key={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis,
                    redactApiKey(request.getHeader(API_KEY_HEADER)));
        }
    }

    private String redactApiKey(String apiKeyHeader) {
        return apiKeyHeader == null || apiKeyHeader.isBlank() ? "absent" : "[REDACTED]";
    }
}
