package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspaceUsageStore implements WorkspaceUsageStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceUsageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkspaceUsageLedgerEntry recordSnapshot(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt) {
        return insert(workspaceId, metric, quantity, null, null, source, sourceRef, recordedAt);
    }

    @Override
    public WorkspaceUsageLedgerEntry recordAdditive(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt) {
        return insert(workspaceId, metric, quantity, windowStart, windowEnd, source, sourceRef, recordedAt);
    }

    @Override
    public long currentSnapshot(long workspaceId, WorkspaceUsageMetric metric) {
        Long quantity = jdbcTemplate.query(
                        """
                        SELECT quantity
                        FROM workspace_usage_ledger
                        WHERE workspace_id = ?
                          AND metric_code = ?
                          AND window_start IS NULL
                          AND window_end IS NULL
                        ORDER BY recorded_at DESC, id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getLong("quantity"),
                        workspaceId,
                        metric.name())
                .stream()
                .findFirst()
                .orElse(null);
        return quantity == null ? 0L : quantity;
    }

    @Override
    public long sumInWindow(long workspaceId, WorkspaceUsageMetric metric, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(quantity), 0)
                FROM workspace_usage_ledger
                WHERE workspace_id = ?
                  AND metric_code = ?
                  AND window_start = ?
                  AND window_end = ?
                """,
                Long.class,
                workspaceId,
                metric.name(),
                windowStart,
                windowEnd);
        return total == null ? 0L : total;
    }

    @Override
    public List<WorkspaceUsageLedgerEntry> findRecent(long workspaceId, WorkspaceUsageMetric metric, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, workspace_id, metric_code, quantity, window_start, window_end,
                       source, source_ref, recorded_at
                FROM workspace_usage_ledger
                WHERE workspace_id = ?
                  AND metric_code = ?
                ORDER BY recorded_at DESC, id DESC
                LIMIT ?
                """,
                this::mapRecord,
                workspaceId,
                metric.name(),
                limit);
    }

    private WorkspaceUsageLedgerEntry insert(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_usage_ledger (
                        workspace_id, metric_code, quantity, window_start, window_end, source, source_ref, recorded_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setString(2, metric.name());
            statement.setLong(3, quantity);
            statement.setObject(4, windowStart);
            statement.setObject(5, windowEnd);
            statement.setString(6, sanitize(source, 64));
            statement.setString(7, sanitize(sourceRef, 255));
            statement.setObject(8, recordedAt);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Workspace usage ledger id was not generated");
        }
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, metric_code, quantity, window_start, window_end,
                               source, source_ref, recorded_at
                        FROM workspace_usage_ledger
                        WHERE id = ?
                        """,
                        this::mapRecord,
                        id.longValue())
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private WorkspaceUsageLedgerEntry mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceUsageLedgerEntry(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                WorkspaceUsageMetric.valueOf(resultSet.getString("metric_code")),
                resultSet.getLong("quantity"),
                resultSet.getObject("window_start", OffsetDateTime.class),
                resultSet.getObject("window_end", OffsetDateTime.class),
                resultSet.getString("source"),
                resultSet.getString("source_ref"),
                resultSet.getObject("recorded_at", OffsetDateTime.class));
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
