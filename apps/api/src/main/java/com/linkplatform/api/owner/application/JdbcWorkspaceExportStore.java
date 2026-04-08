package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspaceExportStore implements WorkspaceExportStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkspaceExportStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceExportRecord create(long workspaceId, long requestedByOwnerId, boolean includeClicks, boolean includeSecurityEvents, boolean includeAbuseCases, boolean includeWebhooks, OffsetDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_exports (
                        workspace_id, requested_by_owner_id, status, include_clicks, include_security_events,
                        include_abuse_cases, include_webhooks, created_at
                    ) VALUES (?, ?, 'QUEUED', ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setLong(2, requestedByOwnerId);
            statement.setBoolean(3, includeClicks);
            statement.setBoolean(4, includeSecurityEvents);
            statement.setBoolean(5, includeAbuseCases);
            statement.setBoolean(6, includeWebhooks);
            statement.setObject(7, createdAt);
            return statement;
        }, keyHolder);
        return findById(workspaceId, keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public List<WorkspaceExportRecord> findByWorkspaceId(long workspaceId, int limit) {
        return jdbcTemplate.query(selectSql() + " WHERE workspace_id = ? ORDER BY created_at DESC, id DESC LIMIT ?", this::mapRecord, workspaceId, limit);
    }

    @Override
    public Optional<WorkspaceExportRecord> findById(long workspaceId, long exportId) {
        return jdbcTemplate.query(selectSql() + " WHERE workspace_id = ? AND id = ?", this::mapRecord, workspaceId, exportId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceExportRecord> findCompletedById(long workspaceId, long exportId) {
        return jdbcTemplate.query(
                        selectSql() + " WHERE workspace_id = ? AND id = ? AND status IN ('READY', 'COMPLETED')",
                        this::mapRecord,
                        workspaceId,
                        exportId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceExportRecord> claimNextQueued(OffsetDateTime now) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM workspace_exports
                WHERE status = 'QUEUED'
                ORDER BY created_at ASC, id ASC
                LIMIT 1
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        long id = ids.getFirst();
        jdbcTemplate.update(
                "UPDATE workspace_exports SET status = 'RUNNING', started_at = ? WHERE id = ? AND status = 'QUEUED'",
                now,
                id);
        return jdbcTemplate.query(selectSql() + " WHERE id = ?", this::mapRecord, id).stream().findFirst();
    }

    @Override
    public void markReady(long exportId, JsonNode payload, long payloadSizeBytes, OffsetDateTime completedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_exports
                SET status = 'READY',
                    payload_json = CAST(? AS JSONB),
                    payload_size_bytes = ?,
                    completed_at = ?,
                    failed_at = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                serialize(payload),
                payloadSizeBytes,
                completedAt,
                exportId);
    }

    @Override
    public void markFailed(long exportId, String lastError, OffsetDateTime failedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_exports
                SET status = 'FAILED',
                    failed_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                failedAt,
                shorten(lastError, 1024),
                exportId);
    }

    private String selectSql() {
        return """
                SELECT id, workspace_id, requested_by_owner_id, status, include_clicks, include_security_events,
                       include_abuse_cases, include_webhooks, payload_json, payload_size_bytes, created_at,
                       started_at, completed_at, failed_at, last_error
                FROM workspace_exports
                """;
    }

    private WorkspaceExportRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceExportRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getLong("requested_by_owner_id"),
                resultSet.getString("status"),
                resultSet.getBoolean("include_clicks"),
                resultSet.getBoolean("include_security_events"),
                resultSet.getBoolean("include_abuse_cases"),
                resultSet.getBoolean("include_webhooks"),
                deserialize(resultSet.getString("payload_json")),
                resultSet.getObject("payload_size_bytes", Long.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getObject("failed_at", OffsetDateTime.class),
                resultSet.getString("last_error"));
    }

    private String serialize(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workspace export payload could not be serialized", exception);
        }
    }

    private JsonNode deserialize(String payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workspace export payload could not be deserialized", exception);
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
