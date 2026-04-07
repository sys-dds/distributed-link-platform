package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspacePlanStore implements WorkspacePlanStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspacePlanStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkspacePlanRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT workspace_id, plan_code, active_links_limit, members_limit, api_keys_limit,
                               webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled,
                               created_at, updated_at
                        FROM workspace_plans
                        WHERE workspace_id = ?
                        """,
                        this::mapRecord,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public WorkspacePlanRecord upsertPlan(long workspaceId, WorkspacePlanCode planCode, OffsetDateTime updatedAt) {
        PlanDefaults defaults = PlanDefaults.forCode(planCode);
        jdbcTemplate.update(
                """
                MERGE INTO workspace_plans (
                    workspace_id, plan_code, active_links_limit, members_limit, api_keys_limit,
                    webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled, created_at, updated_at
                )
                KEY (workspace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM workspace_plans WHERE workspace_id = ?), ?), ?)
                """,
                workspaceId,
                planCode.name(),
                defaults.activeLinksLimit(),
                defaults.membersLimit(),
                defaults.apiKeysLimit(),
                defaults.webhooksLimit(),
                defaults.monthlyWebhookDeliveriesLimit(),
                defaults.exportsEnabled(),
                workspaceId,
                updatedAt,
                updatedAt);
        return findByWorkspaceId(workspaceId).orElseThrow();
    }

    private WorkspacePlanRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspacePlanRecord(
                resultSet.getLong("workspace_id"),
                WorkspacePlanCode.valueOf(resultSet.getString("plan_code")),
                resultSet.getInt("active_links_limit"),
                resultSet.getInt("members_limit"),
                resultSet.getInt("api_keys_limit"),
                resultSet.getInt("webhooks_limit"),
                resultSet.getLong("monthly_webhook_deliveries_limit"),
                resultSet.getBoolean("exports_enabled"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    public record PlanDefaults(
            int activeLinksLimit,
            int membersLimit,
            int apiKeysLimit,
            int webhooksLimit,
            long monthlyWebhookDeliveriesLimit,
            boolean exportsEnabled) {

        static PlanDefaults forCode(WorkspacePlanCode planCode) {
            return switch (planCode) {
                case FREE -> new PlanDefaults(100, 5, 10, 5, 10_000L, true);
                case PRO -> new PlanDefaults(5_000, 50, 100, 50, 1_000_000L, true);
                case ENTERPRISE -> new PlanDefaults(100_000, 500, 1_000, 500, 10_000_000L, true);
            };
        }
    }
}
