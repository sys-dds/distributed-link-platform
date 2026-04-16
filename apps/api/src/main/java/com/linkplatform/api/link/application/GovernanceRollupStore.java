package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface GovernanceRollupStore {

    // Current governance endpoints are live-SQL-backed; governance_daily_rollups is reserved for a future rebuild path.
    GovernanceSummaryRecord summary(OffsetDateTime generatedAt);

    List<WebhookRiskRecord> webhookRisk(int limit);

    List<AbuseRiskRecord> abuseRisk(int limit);

    List<OverQuotaWorkspaceRecord> overQuota(int limit);

    record WebhookRiskRecord(
            String workspaceSlug,
            long subscriptionId,
            String name,
            int consecutiveFailures,
            OffsetDateTime lastFailureAt,
            boolean disabled) {
    }

    record AbuseRiskRecord(
            String workspaceSlug,
            String host,
            long signalCount,
            long openCases,
            long quarantinedLinks) {
    }

    record OverQuotaWorkspaceRecord(
            String workspaceSlug,
            String planCode,
            String metric,
            long currentUsage,
            long limit) {
    }
}
