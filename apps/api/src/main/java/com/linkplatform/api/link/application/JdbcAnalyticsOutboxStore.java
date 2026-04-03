package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAnalyticsOutboxStore implements AnalyticsOutboxStore {

    private static final String REDIRECT_CLICK_EVENT_TYPE = "redirect-click";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAnalyticsOutboxStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
    public List<AnalyticsOutboxRecord> findUnpublished(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at
                FROM analytics_outbox
                WHERE published_at IS NULL
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new AnalyticsOutboxRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("event_key"),
                        resultSet.getString("payload_json"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("published_at", OffsetDateTime.class)),
                limit);
    }

    @Override
    public void markPublished(long id, OffsetDateTime publishedAt) {
        jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET published_at = ?
                WHERE id = ?
                  AND published_at IS NULL
                """,
                publishedAt,
                id);
    }

    private String serialize(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
        try {
            return objectMapper.writeValueAsString(redirectClickAnalyticsEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analytics outbox payload could not be serialized", exception);
        }
    }
}
