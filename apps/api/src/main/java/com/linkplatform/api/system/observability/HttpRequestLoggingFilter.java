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
                    "http_request method={} path={} status={} outcome={} duration_ms={} auth_mode={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    requestOutcome(response.getStatus()),
                    durationMillis,
                    authMode(request));
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
}
