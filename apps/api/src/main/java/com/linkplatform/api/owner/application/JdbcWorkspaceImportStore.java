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
public class JdbcWorkspaceImportStore implements WorkspaceImportStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkspaceImportStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceImportRecord create(
            long workspaceId,
            long requestedByOwnerId,
            Long sourceExportId,
            boolean dryRun,
            boolean overwriteConflicts,
            JsonNode payloadJson,
            OffsetDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_import_jobs (
                        workspace_id, requested_by_owner_id, source_export_id, status, dry_run,
                        overwrite_conflicts, payload_json, created_at
                    ) VALUES (?, ?, ?, 'QUEUED', ?, ?, CAST(? AS JSONB), ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setLong(2, requestedByOwnerId);
            if (sourceExportId == null) {
                statement.setNull(3, java.sql.Types.BIGINT);
            } else {
                statement.setLong(3, sourceExportId);
            }
            statement.setBoolean(4, dryRun);
            statement.setBoolean(5, overwriteConflicts);
            statement.setString(6, serialize(payloadJson));
            statement.setObject(7, createdAt);
            return statement;
        }, keyHolder);
        return findById(workspaceId, keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public List<WorkspaceImportRecord> findByWorkspaceId(long workspaceId, int limit) {
        return jdbcTemplate.query(selectSql() + " WHERE workspace_id = ? ORDER BY created_at DESC, id DESC LIMIT ?", this::mapRecord, workspaceId, limit);
    }

    @Override
    public Optional<WorkspaceImportRecord> findById(long workspaceId, long importId) {
        return jdbcTemplate.query(selectSql() + " WHERE workspace_id = ? AND id = ?", this::mapRecord, workspaceId, importId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceImportRecord> claimNextQueued(OffsetDateTime now) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM workspace_import_jobs
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
                "UPDATE workspace_import_jobs SET status = 'RUNNING', started_at = ? WHERE id = ? AND status = 'QUEUED'",
                now,
                id);
        return jdbcTemplate.query(selectSql() + " WHERE id = ?", this::mapRecord, id).stream().findFirst();
    }

    @Override
    public void markReadyToApply(long importId, JsonNode summaryJson, OffsetDateTime completedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_import_jobs
                SET status = 'READY_TO_APPLY',
                    summary_json = CAST(? AS JSONB),
                    completed_at = ?,
                    failed_at = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                serialize(summaryJson),
                completedAt,
                importId);
    }

    @Override
    public void markCompleted(long importId, JsonNode summaryJson, OffsetDateTime completedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_import_jobs
                SET status = 'COMPLETED',
                    summary_json = CAST(? AS JSONB),
                    completed_at = ?,
                    failed_at = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                serialize(summaryJson),
                completedAt,
                importId);
    }

    @Override
    public void markFailed(long importId, String lastError, OffsetDateTime failedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_import_jobs
                SET status = 'FAILED',
                    failed_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                failedAt,
                shorten(lastError, 1024),
                importId);
    }

    private String selectSql() {
        return """
                SELECT id, workspace_id, requested_by_owner_id, source_export_id, status, dry_run,
                       overwrite_conflicts, payload_json, summary_json, created_at, started_at,
                       completed_at, failed_at, last_error
                FROM workspace_import_jobs
                """;
    }

    private WorkspaceImportRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceImportRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getLong("requested_by_owner_id"),
                resultSet.getObject("source_export_id", Long.class),
                resultSet.getString("status"),
                resultSet.getBoolean("dry_run"),
                resultSet.getBoolean("overwrite_conflicts"),
                deserialize(resultSet.getString("payload_json")),
                deserialize(resultSet.getString("summary_json")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getObject("failed_at", OffsetDateTime.class),
                resultSet.getString("last_error"));
    }

    private String serialize(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workspace import JSON could not be serialized", exception);
        }
    }

    private JsonNode deserialize(String payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workspace import JSON could not be deserialized", exception);
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
