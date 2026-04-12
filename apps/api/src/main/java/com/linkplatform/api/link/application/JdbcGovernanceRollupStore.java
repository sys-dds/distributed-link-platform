package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;
import com.linkplatform.api.owner.application.JdbcWebhookSubscriptionsStore;
import com.linkplatform.api.owner.application.WorkspacePlanStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcGovernanceRollupStore implements GovernanceRollupStore {

    // Option B for TICKET-064: these reads intentionally compute live SQL and do not consume governance_daily_rollups.
    private final JdbcTemplate jdbcTemplate;
    private final WorkspacePlanStore workspacePlanStore;
    private final JdbcWebhookSubscriptionsStore webhookSubscriptionsStore;
    private final JdbcLinkAbuseStore linkAbuseStore;

    public JdbcGovernanceRollupStore(
            JdbcTemplate jdbcTemplate,
            WorkspacePlanStore workspacePlanStore,
            JdbcWebhookSubscriptionsStore webhookSubscriptionsStore,
            JdbcLinkAbuseStore linkAbuseStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.workspacePlanStore = workspacePlanStore;
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.linkAbuseStore = linkAbuseStore;
    }

    @Override
    public GovernanceSummaryRecord summary(OffsetDateTime generatedAt) {
        Long totalWorkspaces = queryLong("SELECT COUNT(*) FROM workspaces WHERE archived_at IS NULL");
        Long suspendedWorkspaces = queryLong("SELECT COUNT(*) FROM workspaces WHERE archived_at IS NULL AND status = 'SUSPENDED'");
        Long totalMembers = queryLong("""
                SELECT COUNT(*)
                FROM workspace_members
                WHERE removed_at IS NULL
                  AND suspended_at IS NULL
                """);
        Long totalServiceAccounts = queryLong("SELECT COUNT(*) FROM service_accounts WHERE status = 'ACTIVE'");
        long totalOpenAbuseCases = linkAbuseStore.countGlobalCasesByStatus(LinkAbuseCaseStatus.OPEN);
        Long totalQuarantinedLinks = queryLong("SELECT COUNT(*) FROM links WHERE abuse_status = 'QUARANTINED'");
        long totalFailingWebhookSubscriptions = webhookSubscriptionsStore.countGlobalFailingSubscriptions();
        long totalOverQuotaWorkspaces = workspacePlanStore.countOverQuotaWorkspaces();
        return new GovernanceSummaryRecord(
                value(totalWorkspaces),
                value(suspendedWorkspaces),
                value(totalMembers),
                value(totalServiceAccounts),
                totalOpenAbuseCases,
                value(totalQuarantinedLinks),
                totalFailingWebhookSubscriptions,
                totalOverQuotaWorkspaces,
                generatedAt);
    }

    @Override
    public List<WebhookRiskRecord> webhookRisk(int limit) {
        return jdbcTemplate.query(
                """
                SELECT w.slug AS workspace_slug,
                       s.id AS subscription_id,
                       s.name,
                       s.consecutive_failures,
                       s.last_failure_at,
                       (s.enabled = FALSE OR s.disabled_at IS NOT NULL) AS disabled
                FROM webhook_subscriptions s
                JOIN workspaces w ON w.id = s.workspace_id
                WHERE s.consecutive_failures > 0
                   OR s.disabled_at IS NOT NULL
                ORDER BY s.consecutive_failures DESC, s.last_failure_at DESC NULLS LAST, s.id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new WebhookRiskRecord(
                        resultSet.getString("workspace_slug"),
                        resultSet.getLong("subscription_id"),
                        resultSet.getString("name"),
                        resultSet.getInt("consecutive_failures"),
                        resultSet.getObject("last_failure_at", OffsetDateTime.class),
                        resultSet.getBoolean("disabled")),
                limit);
    }

    @Override
    public List<AbuseRiskRecord> abuseRisk(int limit) {
        return jdbcTemplate.query(
                """
                SELECT w.slug AS workspace_slug,
                       c.target_host AS host,
                       SUM(c.signal_count) AS signal_count,
                       COUNT(*) FILTER (WHERE c.status = 'OPEN') AS open_cases,
                       (
                           SELECT COUNT(*)
                           FROM links l
                           WHERE l.workspace_id = c.workspace_id
                             AND l.hostname = c.target_host
                             AND l.abuse_status = 'QUARANTINED'
                       ) AS quarantined_links
                FROM link_abuse_cases c
                JOIN workspaces w ON w.id = c.workspace_id
                WHERE c.target_host IS NOT NULL
                  AND c.target_host <> ''
                GROUP BY w.slug, c.workspace_id, c.target_host
                ORDER BY SUM(c.signal_count) DESC, open_cases DESC, quarantined_links DESC, c.target_host ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new AbuseRiskRecord(
                        resultSet.getString("workspace_slug"),
                        resultSet.getString("host"),
                        resultSet.getLong("signal_count"),
                        resultSet.getLong("open_cases"),
                        resultSet.getLong("quarantined_links")),
                limit);
    }

    @Override
    public List<OverQuotaWorkspaceRecord> overQuota(int limit) {
        return jdbcTemplate.query(
                overQuotaSql() + """
                 ORDER BY workspace_slug ASC, metric ASC
                 LIMIT ?
                """,
                (resultSet, rowNum) -> new OverQuotaWorkspaceRecord(
                        resultSet.getString("workspace_slug"),
                        resultSet.getString("plan_code"),
                        resultSet.getString("metric"),
                        resultSet.getLong("current_usage"),
                        resultSet.getLong("limit_value")),
                limit);
    }

    private Long queryLong(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String overQuotaSql() {
        return """
                SELECT w.slug AS workspace_slug,
                       p.plan_code,
                       usage.metric,
                       usage.current_usage,
                       usage.limit_value
                FROM workspaces w
                JOIN workspace_plans p ON p.workspace_id = w.id
                JOIN LATERAL (
                    SELECT 'ACTIVE_LINKS' AS metric,
                           COUNT(l.slug)::BIGINT AS current_usage,
                           p.active_links_limit::BIGINT AS limit_value
                    FROM links l
                    WHERE l.workspace_id = w.id
                      AND l.lifecycle_state = 'ACTIVE'
                    UNION ALL
                    SELECT 'MEMBERS',
                           COUNT(wm.owner_id)::BIGINT,
                           p.members_limit::BIGINT
                    FROM workspace_members wm
                    WHERE wm.workspace_id = w.id
                      AND wm.removed_at IS NULL
                      AND wm.suspended_at IS NULL
                    UNION ALL
                    SELECT 'API_KEYS',
                           COUNT(k.id)::BIGINT,
                           p.api_keys_limit::BIGINT
                    FROM owner_api_keys k
                    WHERE k.workspace_id = w.id
                      AND k.revoked_at IS NULL
                      AND (k.expires_at IS NULL OR k.expires_at > CURRENT_TIMESTAMP)
                    UNION ALL
                    SELECT 'WEBHOOKS',
                           COUNT(s.id)::BIGINT,
                           p.webhooks_limit::BIGINT
                    FROM webhook_subscriptions s
                    WHERE s.workspace_id = w.id
                      AND s.enabled = TRUE
                      AND s.disabled_at IS NULL
                ) usage ON usage.current_usage > usage.limit_value
                WHERE w.archived_at IS NULL
                """;
    }
}
