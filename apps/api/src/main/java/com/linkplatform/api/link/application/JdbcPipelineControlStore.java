package com.linkplatform.api.link.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcPipelineControlStore implements PipelineControlStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPipelineControlStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PipelineControl get(String pipelineName) {
        return jdbcTemplate.query(
                        """
                        SELECT pipeline_name, paused, pause_reason, updated_at, last_force_tick_at, last_requeue_at,
                               last_relay_success_at, last_relay_failure_at, last_relay_failure_reason
                        FROM pipeline_controls
                        WHERE pipeline_name = ?
                        """,
                        (resultSet, rowNum) -> mapControl(resultSet),
                        pipelineName)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pipeline control not found: " + pipelineName));
    }

    @Override
    public PipelineControl pause(String pipelineName, String reason, OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET paused = TRUE,
                    pause_reason = ?,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                truncate(trimToNull(reason), PipelineControl.MAX_PAUSE_REASON_LENGTH),
                updatedAt,
                pipelineName);
        return get(pipelineName);
    }

    @Override
    public PipelineControl resume(String pipelineName, OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET paused = FALSE,
                    pause_reason = NULL,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                updatedAt,
                pipelineName);
        return get(pipelineName);
    }

    @Override
    public PipelineControl recordForceTick(String pipelineName, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET last_force_tick_at = ?,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                occurredAt,
                occurredAt,
                pipelineName);
        return get(pipelineName);
    }

    @Override
    public PipelineControl recordRequeue(String pipelineName, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET last_requeue_at = ?,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                occurredAt,
                occurredAt,
                pipelineName);
        return get(pipelineName);
    }

    @Override
    public PipelineControl recordRelaySuccess(String pipelineName, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET last_relay_success_at = ?,
                    last_relay_failure_reason = NULL,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                occurredAt,
                occurredAt,
                pipelineName);
        return get(pipelineName);
    }

    @Override
    public PipelineControl recordRelayFailure(String pipelineName, OffsetDateTime occurredAt, String failureReason) {
        jdbcTemplate.update(
                """
                UPDATE pipeline_controls
                SET last_relay_failure_at = ?,
                    last_relay_failure_reason = ?,
                    updated_at = ?
                WHERE pipeline_name = ?
                """,
                occurredAt,
                truncate(trimToNull(failureReason), PipelineControl.MAX_FAILURE_REASON_LENGTH),
                occurredAt,
                pipelineName);
        return get(pipelineName);
    }

    private PipelineControl mapControl(ResultSet resultSet) throws SQLException {
        return new PipelineControl(
                resultSet.getString("pipeline_name"),
                resultSet.getBoolean("paused"),
                resultSet.getString("pause_reason"),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("last_force_tick_at", OffsetDateTime.class),
                resultSet.getObject("last_requeue_at", OffsetDateTime.class),
                resultSet.getObject("last_relay_success_at", OffsetDateTime.class),
                resultSet.getObject("last_relay_failure_at", OffsetDateTime.class),
                resultSet.getString("last_relay_failure_reason"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
