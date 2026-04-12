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
public class JdbcWorkspaceRecoveryDrillStore implements WorkspaceRecoveryDrillStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkspaceRecoveryDrillStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceRecoveryDrillRecord create(
            long workspaceId,
            long requestedByOwnerId,
            long sourceExportId,
            boolean dryRun,
            String targetMode,
            OffsetDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_recovery_drills (
                        workspace_id, requested_by_owner_id, source_export_id, status, dry_run, target_mode, created_at
                    ) VALUES (?, ?, ?, 'QUEUED', ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setLong(2, requestedByOwnerId);
            statement.setLong(3, sourceExportId);
            statement.setBoolean(4, dryRun);
            statement.setString(5, targetMode);
            statement.setObject(6, createdAt);
            return statement;
        }, keyHolder);
        return findById(workspaceId, keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public List<WorkspaceRecoveryDrillRecord> findByWorkspaceId(long workspaceId, int limit) {
        return jdbcTemplate.query(
                selectSql() + " WHERE workspace_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                this::mapRecord,
                workspaceId,
                limit);
    }

    @Override
    public Optional<WorkspaceRecoveryDrillRecord> findById(long workspaceId, long drillId) {
        return jdbcTemplate.query(selectSql() + " WHERE workspace_id = ? AND id = ?", this::mapRecord, workspaceId, drillId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceRecoveryDrillRecord> claimNextQueued(OffsetDateTime startedAt) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM workspace_recovery_drills
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
                "UPDATE workspace_recovery_drills SET status = 'RUNNING', started_at = ? WHERE id = ? AND status = 'QUEUED'",
                startedAt,
                id);
        return jdbcTemplate.query(selectSql() + " WHERE id = ?", this::mapRecord, id).stream().findFirst();
    }

    @Override
    public void markCompleted(long drillId, JsonNode summaryJson, OffsetDateTime completedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_recovery_drills
                SET status = 'COMPLETED',
                    summary_json = CAST(? AS JSONB),
                    completed_at = ?,
                    failed_at = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                serialize(summaryJson),
                completedAt,
                drillId);
    }

    @Override
    public void markFailed(long drillId, String lastError, OffsetDateTime failedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_recovery_drills
                SET status = 'FAILED',
                    failed_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                failedAt,
                shorten(lastError),
                drillId);
    }

    private String selectSql() {
        return """
                SELECT id, workspace_id, requested_by_owner_id, source_export_id, status, dry_run, target_mode,
                       summary_json, created_at, started_at, completed_at, failed_at, last_error
                FROM workspace_recovery_drills
                """;
    }

    private WorkspaceRecoveryDrillRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceRecoveryDrillRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getLong("requested_by_owner_id"),
                resultSet.getLong("source_export_id"),
                resultSet.getString("status"),
                resultSet.getBoolean("dry_run"),
                resultSet.getString("target_mode"),
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
            throw new IllegalArgumentException("Recovery drill JSON could not be serialized", exception);
        }
    }

    private JsonNode deserialize(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Recovery drill JSON could not be deserialized", exception);
        }
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 1024 ? trimmed : trimmed.substring(0, 1024);
    }
}
