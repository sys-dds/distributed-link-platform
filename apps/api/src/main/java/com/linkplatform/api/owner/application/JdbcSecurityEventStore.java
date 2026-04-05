package com.linkplatform.api.owner.application;

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
                remoteAddress,
                detailSummary,
                occurredAt);
    }
}
