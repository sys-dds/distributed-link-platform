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

    @Override
    @Transactional(readOnly = true)
    public List<SecurityEventRecord> findEvents(long ownerId, SecurityEventQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, event_type, occurred_at
                FROM owner_security_events
                WHERE owner_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(ownerId);
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
            case MISSING_CREDENTIAL -> "Missing credential rejected";
            case MALFORMED_BEARER -> "Malformed bearer rejected";
            case AMBIGUOUS_CREDENTIAL -> "Conflicting credential rejected";
            case INVALID_CREDENTIAL -> "Invalid credential rejected";
            case RATE_LIMIT_REJECTED -> "Rate limit rejected";
            case QUOTA_REJECTED -> "Quota limit rejected";
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
