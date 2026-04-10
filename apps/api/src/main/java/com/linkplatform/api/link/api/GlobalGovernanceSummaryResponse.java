package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.GovernanceSummaryRecord;
import java.time.OffsetDateTime;

public record GlobalGovernanceSummaryResponse(
        long totalWorkspaces,
        long suspendedWorkspaces,
        long totalMembers,
        long totalServiceAccounts,
        long totalOpenAbuseCases,
        long totalQuarantinedLinks,
        long totalFailingWebhookSubscriptions,
        long totalOverQuotaWorkspaces,
        OffsetDateTime generatedAt) {

    public static GlobalGovernanceSummaryResponse from(GovernanceSummaryRecord record) {
        return new GlobalGovernanceSummaryResponse(
                record.totalWorkspaces(),
                record.suspendedWorkspaces(),
                record.totalMembers(),
                record.totalServiceAccounts(),
                record.totalOpenAbuseCases(),
                record.totalQuarantinedLinks(),
                record.totalFailingWebhookSubscriptions(),
                record.totalOverQuotaWorkspaces(),
                record.generatedAt());
    }
}
