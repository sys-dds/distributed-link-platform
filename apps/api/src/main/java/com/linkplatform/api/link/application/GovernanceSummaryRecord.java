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
        OffsetDateTime generatedAt) {
}
