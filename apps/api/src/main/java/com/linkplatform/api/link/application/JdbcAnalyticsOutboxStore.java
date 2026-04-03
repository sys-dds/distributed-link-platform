package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcAnalyticsOutboxStore implements AnalyticsOutboxStore {

    private static final String REDIRECT_CLICK_EVENT_TYPE = "redirect-click";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public JdbcAnalyticsOutboxStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        Gauge.builder("link.analytics.outbox.unpublished", this, JdbcAnalyticsOutboxStore::countUnpublished)
                .description("Number of unpublished analytics outbox records")
                .register(meterRegistry);
    }

    @Override
    public void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json)
                VALUES (?, ?, ?, ?)
                """,
                redirectClickAnalyticsEvent.eventId(),
                REDIRECT_CLICK_EVENT_TYPE,
                redirectClickAnalyticsEvent.eventKey(),
                serialize(redirectClickAnalyticsEvent));
    }

    @Override
    public List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM analytics_outbox
                    WHERE published_at IS NULL
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """,
                    (resultSet, rowNum) -> resultSet.getLong("id"),
                    now,
                    limit);
            if (ids.isEmpty()) {
                return List.of();
            }

            String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));

            List<Object> updateParameters = new ArrayList<>();
            updateParameters.add(workerId);
            updateParameters.add(claimedUntil);
            updateParameters.addAll(ids);
            jdbcTemplate.update(
                    """
                    UPDATE analytics_outbox
                    SET claimed_by = ?, claimed_until = ?
                    WHERE id IN (%s)
                    """.formatted(placeholders),
                    updateParameters.toArray());

            return jdbcTemplate.query(
                    """
                    SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at, claimed_by, claimed_until
                    FROM analytics_outbox
                    WHERE id IN (%s)
                    ORDER BY created_at ASC, id ASC
                    """.formatted(placeholders),
                    (resultSet, rowNum) -> mapRecord(resultSet),
                    ids.toArray());
        });
    }

    @Override
    public long countUnpublished() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public void markPublished(long id, OffsetDateTime publishedAt) {
        jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET published_at = ?, claimed_by = NULL, claimed_until = NULL
                WHERE id = ?
                  AND published_at IS NULL
                """,
                publishedAt,
                id);
    }

    private AnalyticsOutboxRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new AnalyticsOutboxRecord(
                resultSet.getLong("id"),
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getString("event_key"),
                resultSet.getString("payload_json"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getString("claimed_by"),
                resultSet.getObject("claimed_until", OffsetDateTime.class));
    }

    private String serialize(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
        try {
            return objectMapper.writeValueAsString(redirectClickAnalyticsEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analytics outbox payload could not be serialized", exception);
        }
    }
}
