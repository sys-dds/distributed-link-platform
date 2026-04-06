package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcSecurityEventStore implements SecurityEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSecurityEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SecurityEventType eventType,
            Long ownerId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_security_events (
                    event_type, owner_id, api_key_hash, request_method, request_path, remote_address, detail_summary, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventType.name(),
                ownerId,
                apiKeyHash,
                requestMethod,
                requestPath,
                sanitizeRemoteAddress(remoteAddress),
                shorten(detailSummary),
                occurredAt);
    }

    private String shorten(String detailSummary) {
        if (detailSummary == null) {
            return "unspecified";
        }
        String trimmed = detailSummary.trim();
        if (trimmed.isEmpty()) {
            return "unspecified";
        }
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private String sanitizeRemoteAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        return sha256(remoteAddress.trim());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
