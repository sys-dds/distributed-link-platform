package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcSecurityEventStore implements SecurityEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSecurityEventStore(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SecurityEventType eventType,
            Long ownerId,
            Long workspaceId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_security_events (
                    event_type, owner_id, workspace_id, api_key_hash, request_method, request_path, remote_address, detail_summary, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventType.name(),
                ownerId,
                workspaceId,
                apiKeyHash,
                requestMethod,
                requestPath,
                sanitizeRemoteAddress(remoteAddress),
                shorten(detailSummary),
                occurredAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityEventRecord> findEvents(long workspaceId, SecurityEventQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, event_type, occurred_at
                FROM owner_security_events
                WHERE workspace_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        if (query.types() != null && !query.types().isEmpty()) {
            sql.append(" AND event_type IN (");
            sql.append(query.types().stream().map(ignored -> "?").reduce((left, right) -> left + ", " + right).orElse(""));
            sql.append(')');
            query.types().stream()
                    .map(SecurityEventType::name)
                    .forEach(parameters::add);
        }
        if (query.since() != null) {
            sql.append(" AND occurred_at >= ?");
            parameters.add(query.since());
        }
        DecodedCursor cursor = decodeCursor(query.cursor());
        if (cursor != null) {
            sql.append("""
                     AND (
                         occurred_at < ?
                         OR (occurred_at = ? AND id < ?)
                     )
                    """);
            parameters.add(cursor.occurredAt());
            parameters.add(cursor.occurredAt());
            parameters.add(cursor.id());
        }
        sql.append("""
                ORDER BY occurred_at DESC, id DESC
                LIMIT ?
                """);
        parameters.add(query.limit() + 1);
        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new SecurityEventRecord(
                        resultSet.getLong("id"),
                        SecurityEventType.valueOf(resultSet.getString("event_type")),
                        resultSet.getObject("occurred_at", OffsetDateTime.class),
                        summarize(SecurityEventType.valueOf(resultSet.getString("event_type"))),
                        null),
                parameters.toArray());
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

    private String summarize(SecurityEventType eventType) {
        return switch (eventType) {
            case API_KEY_CREATED -> "API key created";
            case API_KEY_REVOKED -> "API key revoked";
            case API_KEY_ROTATED -> "API key rotated";
            case API_KEY_EXPIRED -> "API key expired";
            case WORKSPACE_CREATED -> "Workspace created";
            case WORKSPACE_INVITATION_CREATED -> "Workspace invitation created";
            case WORKSPACE_INVITATION_ACCEPTED -> "Workspace invitation accepted";
            case WORKSPACE_INVITATION_REVOKED -> "Workspace invitation revoked";
            case SERVICE_ACCOUNT_CREATED -> "Service account created";
            case SERVICE_ACCOUNT_DISABLED -> "Service account disabled";
            case WORKSPACE_MEMBER_ADDED -> "Workspace member added";
            case WORKSPACE_MEMBER_REMOVED -> "Workspace member removed";
            case WORKSPACE_MEMBER_ROLE_CHANGED -> "Workspace member role changed";
            case WORKSPACE_MEMBER_SUSPENDED -> "Workspace member suspended";
            case WORKSPACE_MEMBER_RESUMED -> "Workspace member resumed";
            case WORKSPACE_ACCESS_DENIED -> "Workspace access denied";
            case WORKSPACE_SCOPE_DENIED -> "Workspace scope denied";
            case API_KEY_SCOPE_DENIED -> "API key scope denied";
            case MISSING_CREDENTIAL -> "Missing credential rejected";
            case MALFORMED_BEARER -> "Malformed bearer rejected";
            case AMBIGUOUS_CREDENTIAL -> "Conflicting credential rejected";
            case INVALID_CREDENTIAL -> "Invalid credential rejected";
            case RATE_LIMIT_REJECTED -> "Rate limit rejected";
            case LINK_TARGET_REJECTED -> "Unsafe target rejected";
            case LINK_FLAGGED_FOR_REVIEW -> "Link flagged for review";
            case LINK_QUARANTINED -> "Link quarantined";
            case LINK_RELEASED -> "Link released";
            case LINK_ABUSE_DISMISSED -> "Link abuse case dismissed";
            case LINK_QUARANTINED_REDIRECT_ATTEMPT -> "Quarantined redirect blocked";
            case ABUSE_CASE_OPENED -> "Abuse case opened";
            case ABUSE_CASE_SIGNAL_INCREMENTED -> "Abuse case signal incremented";
            case WORKSPACE_ABUSE_POLICY_UPDATED -> "Workspace abuse policy updated";
            case WORKSPACE_HOST_RULE_CREATED -> "Workspace host rule created";
            case WORKSPACE_HOST_RULE_DELETED -> "Workspace host rule deleted";
            case QUOTA_REJECTED -> "Quota limit rejected";
            case WORKSPACE_PLAN_UPDATED -> "Workspace plan updated";
            case WORKSPACE_SUBSCRIPTION_STATE_CHANGED -> "Workspace subscription changed";
            case WORKSPACE_PLAN_CHANGE_SCHEDULED -> "Workspace plan scheduled";
            case WORKSPACE_SUSPENDED -> "Workspace suspended";
            case WORKSPACE_RESUMED -> "Workspace resumed";
            case WORKSPACE_OWNERSHIP_TRANSFERRED -> "Workspace ownership transferred";
            case WORKSPACE_QUOTA_EXCEEDED -> "Workspace quota exceeded";
            case WEBHOOK_DELIVERY_QUOTA_EXCEEDED -> "Webhook delivery quota exceeded";
            case WEBHOOK_SUBSCRIPTION_CREATED -> "Webhook subscription created";
            case WEBHOOK_SUBSCRIPTION_UPDATED -> "Webhook subscription updated";
            case WEBHOOK_SECRET_ROTATED -> "Webhook secret rotated";
            case WEBHOOK_SUBSCRIPTION_VERIFIED -> "Webhook subscription verified";
            case WEBHOOK_TEST_FIRED -> "Webhook test fired";
            case WEBHOOK_DELIVERY_REPLAYED -> "Webhook delivery replayed";
            case WEBHOOK_SUBSCRIPTION_DISABLED -> "Webhook subscription disabled";
            case WORKSPACE_EXPORT_REQUESTED -> "Workspace export requested";
            case WORKSPACE_EXPORT_COMPLETED -> "Workspace export completed";
            case WORKSPACE_EXPORT_FAILED -> "Workspace export failed";
            case WORKSPACE_IMPORT_REQUESTED -> "Workspace import requested";
            case WORKSPACE_IMPORT_COMPLETED -> "Workspace import completed";
            case WORKSPACE_IMPORT_FAILED -> "Workspace import failed";
            case WORKSPACE_RECOVERY_DRILL_REQUESTED -> "Workspace recovery drill requested";
            case WORKSPACE_RECOVERY_DRILL_COMPLETED -> "Workspace recovery drill completed";
            case WORKSPACE_RECOVERY_DRILL_FAILED -> "Workspace recovery drill failed";
            case WORKSPACE_ENTERPRISE_POLICY_UPDATED -> "Workspace enterprise policy updated";

            case PRIVILEGED_ACTION_APPROVAL_REQUESTED -> "Privileged action approval requested";
            case PRIVILEGED_ACTION_APPROVED -> "Privileged action approved";

            case API_KEY_EXPIRY_POLICY_VIOLATION -> "API key expiry policy violation";
            case WORKSPACE_RETENTION_PURGE_RUN -> "Workspace retention purge run";
            case ANALYTICS_PIPELINE_PAUSED -> "Analytics pipeline paused";
            case ANALYTICS_PIPELINE_RESUMED -> "Analytics pipeline resumed";
            case ANALYTICS_PIPELINE_FORCE_TICKED -> "Analytics pipeline force ticked";
            case ANALYTICS_PIPELINE_DRAINED -> "Analytics pipeline drained";
            case LIFECYCLE_PIPELINE_PAUSED -> "Lifecycle pipeline paused";
            case LIFECYCLE_PIPELINE_RESUMED -> "Lifecycle pipeline resumed";
            case LIFECYCLE_PIPELINE_FORCE_TICKED -> "Lifecycle pipeline force ticked";
            case LIFECYCLE_PIPELINE_DRAINED -> "Lifecycle pipeline drained";
            case CLICK_ROLLUP_DRIFT_DETECTED -> "Click rollup drift detected";
            case CLICK_ROLLUP_REPAIRED -> "Click rollup repaired";
            case QUERY_DATASOURCE_FALLBACK -> "Query datasource fallback activated";
            case QUERY_REPLICA_FALLBACK_TRIGGERED -> "Query replica fallback triggered";
            case QUERY_REPLICA_STALE -> "Query replica stale";
            case RUNTIME_CONFIGURATION_REJECTED -> "Runtime configuration rejected";
            case REDIRECT_LOOKUP_FAILED -> "Redirect lookup failed";
            case REDIRECT_FAILOVER_ACTIVATED -> "Redirect failover activated";
            case REDIRECT_UNAVAILABLE -> "Redirect unavailable";
        };
    }

    private DecodedCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiterIndex = decoded.lastIndexOf('|');
            if (delimiterIndex <= 0 || delimiterIndex == decoded.length() - 1) {
                throw new IllegalArgumentException("Cursor is invalid");
            }
            return new DecodedCursor(
                    OffsetDateTime.parse(decoded.substring(0, delimiterIndex)),
                    Long.parseLong(decoded.substring(delimiterIndex + 1)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("Cursor is invalid");
        }
    }

    private record DecodedCursor(OffsetDateTime occurredAt, long id) {
    }
}
