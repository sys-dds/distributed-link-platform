package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcWebhookDeliveryStore implements WebhookDeliveryStore {

    private static final TypeReference<List<String>> EVENT_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public JdbcWebhookDeliveryStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public WebhookDeliveryRecord create(
            long subscriptionId,
            long workspaceId,
            WebhookEventType eventType,
            String eventId,
            JsonNode payload,
            WebhookDeliveryStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime nextAttemptAt) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        """
                        INSERT INTO webhook_deliveries (
                            subscription_id, workspace_id, event_type, event_id, payload_json, status,
                            attempt_count, next_attempt_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, 0, ?, ?, ?)
                        """,
                        new String[] {"id"});
                statement.setLong(1, subscriptionId);
                statement.setLong(2, workspaceId);
                statement.setString(3, eventType.value());
                statement.setString(4, eventId);
                statement.setString(5, serialize(payload));
                statement.setString(6, status.name());
                statement.setObject(7, nextAttemptAt);
                statement.setObject(8, createdAt);
                statement.setObject(9, createdAt);
                return statement;
            }, keyHolder);
            return findById(workspaceId, subscriptionId, keyHolder.getKey().longValue()).orElseThrow();
        } catch (DuplicateKeyException exception) {
            return findBySubscriptionAndEventId(subscriptionId, eventId)
                    .orElseThrow(() -> exception);
        }
    }

    @Override
    public java.util.Optional<WebhookDeliveryRecord> findBySubscriptionAndEventId(long subscriptionId, String eventId) {
        return jdbcTemplate.query(
                        selectSql() + " WHERE d.subscription_id = ? AND d.event_id = ?",
                        this::mapDelivery,
                        subscriptionId,
                        eventId)
                .stream()
                .findFirst();
    }

    @Override
    public List<WebhookDeliveryRecord> findBySubscription(long workspaceId, long subscriptionId, int limit) {
        return jdbcTemplate.query(selectSql() + " WHERE d.workspace_id = ? AND d.subscription_id = ? ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                this::mapDelivery,
                workspaceId,
                subscriptionId,
                limit);
    }

    @Override
    public java.util.Optional<WebhookDeliveryRecord> findById(long workspaceId, long subscriptionId, long deliveryId) {
        return jdbcTemplate.query(selectSql() + " WHERE d.workspace_id = ? AND d.subscription_id = ? AND d.id = ?",
                        this::mapDelivery,
                        workspaceId,
                        subscriptionId,
                        deliveryId)
                .stream()
                .findFirst();
    }

    @Override
    public java.util.Optional<WebhookDeliveryRecord> findLatestBySubscription(long workspaceId, long subscriptionId) {
        return jdbcTemplate.query(
                        selectSql() + " WHERE d.workspace_id = ? AND d.subscription_id = ? ORDER BY d.created_at DESC, d.id DESC LIMIT 1",
                        this::mapDelivery,
                        workspaceId,
                        subscriptionId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DispatchItem> claimDueDeliveries(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = jdbcTemplate.query(
                    """
                    SELECT d.id
                    FROM webhook_deliveries d
                    JOIN webhook_subscriptions s ON s.id = d.subscription_id
                    WHERE d.status IN ('PENDING', 'FAILED')
                      AND s.enabled = TRUE
                      AND s.disabled_at IS NULL
                      AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= ?)
                    ORDER BY d.created_at ASC, d.id ASC
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
            return jdbcTemplate.query(dispatchSql(placeholders), this::mapDispatchItem, ids.toArray());
        });
    }

    @Override
    public void markDelivered(long deliveryId, OffsetDateTime deliveredAt, Integer httpStatus, String responseExcerpt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'DELIVERED',
                    attempt_count = attempt_count + 1,
                    delivered_at = ?,
                    http_status = ?,
                    response_excerpt = ?,
                    updated_at = ?,
                    last_error = NULL,
                    next_attempt_at = NULL,
                    parked_at = NULL
                WHERE id = ?
                """,
                deliveredAt,
                httpStatus,
                shorten(responseExcerpt, 1024),
                deliveredAt,
                deliveryId);
    }

    @Override
    public void markFailed(long deliveryId, int attemptCount, OffsetDateTime nextAttemptAt, Integer httpStatus, String lastError, String responseExcerpt) {
        OffsetDateTime now = OffsetDateTime.now(java.time.Clock.systemUTC());
        jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'FAILED',
                    attempt_count = ?,
                    next_attempt_at = ?,
                    http_status = ?,
                    last_error = ?,
                    response_excerpt = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                attemptCount,
                nextAttemptAt,
                httpStatus,
                shorten(lastError, 1024),
                shorten(responseExcerpt, 1024),
                now,
                deliveryId);
    }

    @Override
    public void markParked(long deliveryId, int attemptCount, String lastError, Integer httpStatus, String responseExcerpt, OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'PARKED',
                    attempt_count = ?,
                    last_error = ?,
                    http_status = ?,
                    response_excerpt = ?,
                    parked_at = ?,
                    updated_at = ?,
                    next_attempt_at = NULL
                WHERE id = ?
                """,
                attemptCount,
                shorten(lastError, 1024),
                httpStatus,
                shorten(responseExcerpt, 1024),
                parkedAt,
                parkedAt,
                deliveryId);
    }

    @Override
    public void markDisabled(long deliveryId, String reason, OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'DISABLED',
                    last_error = ?,
                    updated_at = ?,
                    next_attempt_at = NULL
                WHERE id = ?
                """,
                shorten(reason, 1024),
                updatedAt,
                deliveryId);
    }

    @Override
    public int parkDueDeliveriesForWorkspace(long workspaceId, String reason, OffsetDateTime parkedAt) {
        return jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'PARKED',
                    last_error = ?,
                    parked_at = ?,
                    updated_at = ?,
                    next_attempt_at = NULL
                WHERE workspace_id = ?
                  AND status IN ('PENDING', 'FAILED')
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                """,
                shorten(reason, 1024),
                parkedAt,
                parkedAt,
                workspaceId,
                parkedAt);
    }

    @Override
    public int disableQueuedDeliveriesForSubscription(long workspaceId, long subscriptionId, String reason, OffsetDateTime updatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE webhook_deliveries
                SET status = 'DISABLED',
                    last_error = ?,
                    updated_at = ?,
                    next_attempt_at = NULL
                WHERE workspace_id = ?
                  AND subscription_id = ?
                  AND status IN ('PENDING', 'FAILED', 'PARKED')
                """,
                shorten(reason, 1024),
                updatedAt,
                workspaceId,
                subscriptionId);
    }

    private String selectSql() {
        return """
                SELECT d.id, d.subscription_id, d.workspace_id, w.slug AS workspace_slug,
                       d.event_type, d.event_id, d.payload_json, d.status, d.attempt_count,
                       d.next_attempt_at, d.delivered_at, d.last_error, d.http_status,
                       d.response_excerpt, d.created_at, d.updated_at, d.parked_at
                FROM webhook_deliveries d
                JOIN workspaces w ON w.id = d.workspace_id
                """;
    }

    private String dispatchSql(String placeholders) {
        return """
                SELECT d.id, d.subscription_id, d.workspace_id, w.slug AS workspace_slug,
                       d.event_type, d.event_id, d.payload_json, d.status, d.attempt_count,
                       d.next_attempt_at, d.delivered_at, d.last_error, d.http_status,
                       d.response_excerpt, d.created_at, d.updated_at, d.parked_at,
                       s.name, s.callback_url, s.signing_secret_hash, s.signing_secret_prefix,
                       s.event_version, s.verification_status, s.verified_at, s.enabled, s.event_types_json,
                       s.last_delivery_at, s.last_failure_at, s.consecutive_failures,
                       s.disabled_at, s.disabled_reason, s.last_test_fired_at, s.last_test_delivery_id
                FROM webhook_deliveries d
                JOIN webhook_subscriptions s ON s.id = d.subscription_id
                JOIN workspaces w ON w.id = d.workspace_id
                WHERE d.id IN (%s)
                ORDER BY d.created_at ASC, d.id ASC
                """.formatted(placeholders);
    }

    private DispatchItem mapDispatchItem(ResultSet resultSet, int rowNum) throws SQLException {
        WebhookDeliveryRecord delivery = mapDelivery(resultSet, rowNum);
        WebhookSubscriptionRecord subscription = new WebhookSubscriptionRecord(
                resultSet.getLong("subscription_id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("workspace_slug"),
                resultSet.getString("name"),
                resultSet.getString("callback_url"),
                resultSet.getString("signing_secret_hash"),
                resultSet.getString("signing_secret_prefix"),
                resultSet.getInt("event_version"),
                resultSet.getString("verification_status"),
                resultSet.getObject("verified_at", OffsetDateTime.class),
                resultSet.getBoolean("enabled"),
                deserializeEvents(resultSet.getString("event_types_json")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("last_delivery_at", OffsetDateTime.class),
                resultSet.getObject("last_failure_at", OffsetDateTime.class),
                resultSet.getInt("consecutive_failures"),
                resultSet.getObject("disabled_at", OffsetDateTime.class),
                resultSet.getString("disabled_reason"),
                resultSet.getObject("last_test_fired_at", OffsetDateTime.class),
                resultSet.getObject("last_test_delivery_id", Long.class));
        return new DispatchItem(delivery, subscription);
    }

    private WebhookDeliveryRecord mapDelivery(ResultSet resultSet, int rowNum) throws SQLException {
        return new WebhookDeliveryRecord(
                resultSet.getLong("id"),
                resultSet.getLong("subscription_id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("workspace_slug"),
                WebhookEventType.fromValue(resultSet.getString("event_type")),
                resultSet.getString("event_id"),
                deserialize(resultSet.getString("payload_json")),
                WebhookDeliveryStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("next_attempt_at", OffsetDateTime.class),
                resultSet.getObject("delivered_at", OffsetDateTime.class),
                resultSet.getString("last_error"),
                resultSet.getObject("http_status", Integer.class),
                resultSet.getString("response_excerpt"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("parked_at", OffsetDateTime.class));
    }

    private String serialize(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook delivery payload could not be serialized", exception);
        }
    }

    private JsonNode deserialize(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook delivery payload could not be deserialized", exception);
        }
    }

    private Set<WebhookEventType> deserializeEvents(String json) {
        try {
            return objectMapper.readValue(json, EVENT_LIST_TYPE).stream()
                    .map(WebhookEventType::fromValue)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook event types could not be deserialized", exception);
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
