package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspaceRetentionPolicyStore implements WorkspaceRetentionPolicyStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceRetentionPolicyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkspaceRetentionPolicyRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT workspace_id, click_history_days, security_events_days, webhook_deliveries_days,
                               abuse_cases_days, operator_action_log_days, updated_at, updated_by_owner_id
                        FROM workspace_retention_policies
                        WHERE workspace_id = ?
                        """,
                        this::mapRecord,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public WorkspaceRetentionPolicyRecord upsert(
            long workspaceId,
            int clickHistoryDays,
            int securityEventsDays,
            int webhookDeliveriesDays,
            int abuseCasesDays,
            int operatorActionLogDays,
            OffsetDateTime updatedAt,
            long updatedByOwnerId) {
        jdbcTemplate.update(
                """
                INSERT INTO workspace_retention_policies (
                    workspace_id, click_history_days, security_events_days, webhook_deliveries_days,
                    abuse_cases_days, operator_action_log_days, updated_at, updated_by_owner_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id) DO UPDATE
                SET click_history_days = EXCLUDED.click_history_days,
                    security_events_days = EXCLUDED.security_events_days,
                    webhook_deliveries_days = EXCLUDED.webhook_deliveries_days,
                    abuse_cases_days = EXCLUDED.abuse_cases_days,
                    operator_action_log_days = EXCLUDED.operator_action_log_days,
                    updated_at = EXCLUDED.updated_at,
                    updated_by_owner_id = EXCLUDED.updated_by_owner_id
                """,
                workspaceId,
                clickHistoryDays,
                securityEventsDays,
                webhookDeliveriesDays,
                abuseCasesDays,
                operatorActionLogDays,
                updatedAt,
                updatedByOwnerId);
        return findByWorkspaceId(workspaceId).orElseThrow();
    }

    @Override
    public long purgeSecurityEvents(long workspaceId, OffsetDateTime cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM owner_security_events WHERE workspace_id = ? AND occurred_at < ?",
                workspaceId,
                cutoff);
    }

    @Override
    public long purgeOperatorActions(long workspaceId, OffsetDateTime cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM operator_action_log WHERE workspace_id = ? AND created_at < ?",
                workspaceId,
                cutoff);
    }

    private WorkspaceRetentionPolicyRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceRetentionPolicyRecord(
                resultSet.getLong("workspace_id"),
                resultSet.getInt("click_history_days"),
                resultSet.getInt("security_events_days"),
                resultSet.getInt("webhook_deliveries_days"),
                resultSet.getInt("abuse_cases_days"),
                resultSet.getInt("operator_action_log_days"),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getLong("updated_by_owner_id"));
    }
}
