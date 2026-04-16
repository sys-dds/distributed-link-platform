package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record GovernanceSummaryRecord(
        long totalWorkspaces,
        long suspendedWorkspaces,
        long totalMembers,
        long totalServiceAccounts,
        long totalOpenAbuseCases,
        long totalQuarantinedLinks,
        long totalFailingWebhookSubscriptions,
        long totalOverQuotaWorkspaces,
        // Timestamp of the live SQL snapshot, not a governance_daily_rollups bucket timestamp.
        OffsetDateTime generatedAt) {
}
