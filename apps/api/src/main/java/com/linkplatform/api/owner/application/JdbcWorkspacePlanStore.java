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
                        SELECT workspace_id, plan_code, subscription_status, active_links_limit, members_limit, api_keys_limit,
                               webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled, current_period_start, current_period_end,
                               grace_until, scheduled_plan_code, scheduled_plan_effective_at,
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
                INSERT INTO workspace_plans (
                    workspace_id, plan_code, active_links_limit, members_limit, api_keys_limit,
                    webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id) DO UPDATE
                SET plan_code = EXCLUDED.plan_code,
                    active_links_limit = EXCLUDED.active_links_limit,
                    members_limit = EXCLUDED.members_limit,
                    api_keys_limit = EXCLUDED.api_keys_limit,
                    webhooks_limit = EXCLUDED.webhooks_limit,
                    monthly_webhook_deliveries_limit = EXCLUDED.monthly_webhook_deliveries_limit,
                    exports_enabled = EXCLUDED.exports_enabled,
                    updated_at = EXCLUDED.updated_at
                """,
                workspaceId,
                planCode.name(),
                defaults.activeLinksLimit(),
                defaults.membersLimit(),
                defaults.apiKeysLimit(),
                defaults.webhooksLimit(),
                defaults.monthlyWebhookDeliveriesLimit(),
                defaults.exportsEnabled(),
                updatedAt,
                updatedAt);
        return findByWorkspaceId(workspaceId).orElseThrow();
    }

    @Override
    public WorkspacePlanRecord updateSubscriptionLifecycle(
            long workspaceId,
            WorkspaceSubscriptionStatus subscriptionStatus,
            OffsetDateTime graceUntil,
            WorkspacePlanCode scheduledPlanCode,
            OffsetDateTime scheduledPlanEffectiveAt,
            OffsetDateTime updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE workspace_plans
                SET subscription_status = ?,
                    grace_until = ?,
                    scheduled_plan_code = ?,
                    scheduled_plan_effective_at = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                """,
                subscriptionStatus.name(),
                graceUntil,
                scheduledPlanCode == null ? null : scheduledPlanCode.name(),
                scheduledPlanEffectiveAt,
                updatedAt,
                workspaceId);
        return findByWorkspaceId(workspaceId).orElseThrow();
    }

    @Override
    public long countOverQuotaWorkspaces() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT workspace_slug)
                FROM (
                    SELECT w.slug AS workspace_slug
                    FROM workspaces w
                    JOIN workspace_plans p ON p.workspace_id = w.id
                    JOIN LATERAL (
                        SELECT COUNT(l.slug)::BIGINT AS current_usage, p.active_links_limit::BIGINT AS limit_value
                        FROM links l
                        WHERE l.workspace_id = w.id
                          AND l.lifecycle_state = 'ACTIVE'
                        UNION ALL
                        SELECT COUNT(wm.owner_id)::BIGINT, p.members_limit::BIGINT
                        FROM workspace_members wm
                        WHERE wm.workspace_id = w.id
                          AND wm.removed_at IS NULL
                          AND wm.suspended_at IS NULL
                        UNION ALL
                        SELECT COUNT(k.id)::BIGINT, p.api_keys_limit::BIGINT
                        FROM owner_api_keys k
                        WHERE k.workspace_id = w.id
                          AND k.revoked_at IS NULL
                          AND (k.expires_at IS NULL OR k.expires_at > CURRENT_TIMESTAMP)
                        UNION ALL
                        SELECT COUNT(s.id)::BIGINT, p.webhooks_limit::BIGINT
                        FROM webhook_subscriptions s
                        WHERE s.workspace_id = w.id
                          AND s.enabled = TRUE
                          AND s.disabled_at IS NULL
                    ) usage ON usage.current_usage > usage.limit_value
                    WHERE w.archived_at IS NULL
                ) over_quota
                """,
                Long.class);
        return count == null ? 0L : count;
    }

    private WorkspacePlanRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspacePlanRecord(
                resultSet.getLong("workspace_id"),
                WorkspacePlanCode.valueOf(resultSet.getString("plan_code")),
                WorkspaceSubscriptionStatus.valueOf(resultSet.getString("subscription_status")),
                resultSet.getInt("active_links_limit"),
                resultSet.getInt("members_limit"),
                resultSet.getInt("api_keys_limit"),
                resultSet.getInt("webhooks_limit"),
                resultSet.getLong("monthly_webhook_deliveries_limit"),
                resultSet.getBoolean("exports_enabled"),
                resultSet.getObject("current_period_start", OffsetDateTime.class),
                resultSet.getObject("current_period_end", OffsetDateTime.class),
                resultSet.getObject("grace_until", OffsetDateTime.class),
                mapNullablePlanCode(resultSet.getString("scheduled_plan_code")),
                resultSet.getObject("scheduled_plan_effective_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private WorkspacePlanCode mapNullablePlanCode(String value) {
        return value == null ? null : WorkspacePlanCode.valueOf(value);
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
