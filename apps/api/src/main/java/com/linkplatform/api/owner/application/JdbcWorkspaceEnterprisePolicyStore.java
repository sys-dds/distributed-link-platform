package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspaceEnterprisePolicyStore implements WorkspaceEnterprisePolicyStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceEnterprisePolicyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkspaceEnterprisePolicyRecord findOrCreateDefault(long workspaceId, long ownerId, OffsetDateTime now) {
        return jdbcTemplate.query(
                        """
                        SELECT workspace_id,
                               require_api_key_expiry,
                               max_api_key_ttl_days,
                               require_service_account_key_expiry,
                               max_service_account_key_ttl_days,
                               require_dual_control_for_ops,
                               require_dual_control_for_plan_changes,
                               updated_at,
                               updated_by_owner_id
                        FROM workspace_enterprise_policies
                        WHERE workspace_id = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        workspaceId)
                .stream()
                .findFirst()
                .orElseGet(() -> createDefault(workspaceId, ownerId, now));
    }

    @Override
    public WorkspaceEnterprisePolicyRecord update(
            long workspaceId,
            boolean requireApiKeyExpiry,
            Integer maxApiKeyTtlDays,
            boolean requireServiceAccountKeyExpiry,
            Integer maxServiceAccountKeyTtlDays,
            boolean requireDualControlForOps,
            boolean requireDualControlForPlanChanges,
            OffsetDateTime updatedAt,
            long updatedByOwnerId) {
        jdbcTemplate.update(
                """
                INSERT INTO workspace_enterprise_policies (
                    workspace_id,
                    require_api_key_expiry,
                    max_api_key_ttl_days,
                    require_service_account_key_expiry,
                    max_service_account_key_ttl_days,
                    require_dual_control_for_ops,
                    require_dual_control_for_plan_changes,
                    updated_at,
                    updated_by_owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id) DO UPDATE
                SET require_api_key_expiry = EXCLUDED.require_api_key_expiry,
                    max_api_key_ttl_days = EXCLUDED.max_api_key_ttl_days,
                    require_service_account_key_expiry = EXCLUDED.require_service_account_key_expiry,
                    max_service_account_key_ttl_days = EXCLUDED.max_service_account_key_ttl_days,
                    require_dual_control_for_ops = EXCLUDED.require_dual_control_for_ops,
                    require_dual_control_for_plan_changes = EXCLUDED.require_dual_control_for_plan_changes,
                    updated_at = EXCLUDED.updated_at,
                    updated_by_owner_id = EXCLUDED.updated_by_owner_id
                """,
                workspaceId,
                requireApiKeyExpiry,
                maxApiKeyTtlDays,
                requireServiceAccountKeyExpiry,
                maxServiceAccountKeyTtlDays,
                requireDualControlForOps,
                requireDualControlForPlanChanges,
                updatedAt,
                updatedByOwnerId);
        return findOrCreateDefault(workspaceId, updatedByOwnerId, updatedAt);
    }

    private WorkspaceEnterprisePolicyRecord createDefault(long workspaceId, long ownerId, OffsetDateTime now) {
        return update(
                workspaceId,
                false,
                null,
                false,
                null,
                false,
                false,
                now,
                ownerId);
    }

    private WorkspaceEnterprisePolicyRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new WorkspaceEnterprisePolicyRecord(
                resultSet.getLong("workspace_id"),
                resultSet.getBoolean("require_api_key_expiry"),
                getNullableInt(resultSet, "max_api_key_ttl_days"),
                resultSet.getBoolean("require_service_account_key_expiry"),
                getNullableInt(resultSet, "max_service_account_key_ttl_days"),
                resultSet.getBoolean("require_dual_control_for_ops"),
                resultSet.getBoolean("require_dual_control_for_plan_changes"),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getLong("updated_by_owner_id"));
    }

    private Integer getNullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
