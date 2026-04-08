package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcWebhookSubscriptionsStore implements WebhookSubscriptionsStore {

    private static final TypeReference<List<String>> EVENT_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWebhookSubscriptionsStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WebhookSubscriptionRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(selectSql() + " WHERE s.workspace_id = ? ORDER BY s.created_at DESC, s.id DESC", this::mapRecord, workspaceId);
    }

    @Override
    public Optional<WebhookSubscriptionRecord> findById(long workspaceId, long subscriptionId) {
        return jdbcTemplate.query(selectSql() + " WHERE s.workspace_id = ? AND s.id = ?", this::mapRecord, workspaceId, subscriptionId)
                .stream()
                .findFirst();
    }

    @Override
    public WebhookSubscriptionRecord create(
            long workspaceId,
            String name,
            String callbackUrl,
            String signingSecretHash,
            String signingSecretPrefix,
            boolean enabled,
            Set<WebhookEventType> eventTypes,
            OffsetDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO webhook_subscriptions (
                        workspace_id, name, callback_url, signing_secret_hash, signing_secret_prefix,
                        enabled, event_types_json, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setString(2, sanitize(name, 120));
            statement.setString(3, callbackUrl);
            statement.setString(4, signingSecretHash);
            statement.setString(5, signingSecretPrefix);
            statement.setBoolean(6, enabled);
            statement.setString(7, serializeEvents(eventTypes));
            statement.setObject(8, createdAt);
            statement.setObject(9, createdAt);
            return statement;
        }, keyHolder);
        return findById(workspaceId, keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public WebhookSubscriptionRecord update(
            long workspaceId,
            long subscriptionId,
            String name,
            Boolean enabled,
            Set<WebhookEventType> eventTypes,
            OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET name = COALESCE(?, name),
                    enabled = COALESCE(?, enabled),
                    event_types_json = COALESCE(CAST(? AS JSONB), event_types_json),
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                sanitize(name, 120),
                enabled,
                eventTypes == null ? null : serializeEvents(eventTypes),
                updatedAt,
                workspaceId,
                subscriptionId);
        return findById(workspaceId, subscriptionId).orElseThrow();
    }

    @Override
    public WebhookSubscriptionRecord rotateSecret(
            long workspaceId,
            long subscriptionId,
            String signingSecretHash,
            String signingSecretPrefix,
            OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET signing_secret_hash = ?,
                    signing_secret_prefix = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                signingSecretHash,
                signingSecretPrefix,
                updatedAt,
                workspaceId,
                subscriptionId);
        return findById(workspaceId, subscriptionId).orElseThrow();
    }

    @Override
    public WebhookSubscriptionRecord markVerified(long workspaceId, long subscriptionId, OffsetDateTime verifiedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET verification_status = 'VERIFIED',
                    verified_at = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                verifiedAt,
                verifiedAt,
                workspaceId,
                subscriptionId);
        return findById(workspaceId, subscriptionId).orElseThrow();
    }

    @Override
    public WebhookSubscriptionRecord recordTestFired(long workspaceId, long subscriptionId, long deliveryId, OffsetDateTime firedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET last_test_fired_at = ?,
                    last_test_delivery_id = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                firedAt,
                deliveryId,
                firedAt,
                workspaceId,
                subscriptionId);
        return findById(workspaceId, subscriptionId).orElseThrow();
    }

    @Override
    public long countEnabledByWorkspaceId(long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_subscriptions WHERE workspace_id = ? AND enabled = TRUE AND disabled_at IS NULL",
                Long.class,
                workspaceId);
        return count == null ? 0L : count;
    }

    @Override
    public List<WebhookSubscriptionRecord> findEnabledByWorkspaceIdAndEventType(long workspaceId, WebhookEventType eventType) {
        return findByWorkspaceId(workspaceId).stream()
                .filter(WebhookSubscriptionRecord::enabled)
                .filter(record -> record.disabledAt() == null)
                .filter(record -> record.eventTypes().contains(eventType))
                .toList();
    }

    @Override
    public void recordDeliverySuccess(long workspaceId, long subscriptionId, OffsetDateTime deliveredAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET last_delivery_at = ?,
                    consecutive_failures = 0,
                    last_failure_at = NULL,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                deliveredAt,
                deliveredAt,
                workspaceId,
                subscriptionId);
    }

    @Override
    public int incrementFailureCount(long workspaceId, long subscriptionId, OffsetDateTime failedAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET consecutive_failures = consecutive_failures + 1,
                    last_failure_at = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                failedAt,
                failedAt,
                workspaceId,
                subscriptionId);
        return findById(workspaceId, subscriptionId).map(WebhookSubscriptionRecord::consecutiveFailures).orElse(0);
    }

    @Override
    public void disable(long workspaceId, long subscriptionId, String reason, OffsetDateTime disabledAt) {
        jdbcTemplate.update(
                """
                UPDATE webhook_subscriptions
                SET enabled = FALSE,
                    disabled_at = ?,
                    disabled_reason = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                """,
                disabledAt,
                sanitize(reason, 255),
                disabledAt,
                workspaceId,
                subscriptionId);
    }

    private String selectSql() {
        return """
                SELECT s.id, s.workspace_id, w.slug AS workspace_slug, s.name, s.callback_url,
                       s.signing_secret_hash, s.signing_secret_prefix, s.event_version,
                       s.verification_status, s.verified_at, s.enabled, s.event_types_json,
                       s.created_at, s.updated_at, s.last_delivery_at, s.last_failure_at,
                       s.consecutive_failures, s.disabled_at, s.disabled_reason,
                       s.last_test_fired_at, s.last_test_delivery_id
                FROM webhook_subscriptions s
                JOIN workspaces w ON w.id = s.workspace_id
                """;
    }

    private WebhookSubscriptionRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WebhookSubscriptionRecord(
                resultSet.getLong("id"),
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
    }

    private String serializeEvents(Set<WebhookEventType> eventTypes) {
        try {
            return objectMapper.writeValueAsString(eventTypes.stream().map(WebhookEventType::value).sorted().toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook event types could not be serialized", exception);
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

    private String sanitize(String value, int maxLength) {
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
