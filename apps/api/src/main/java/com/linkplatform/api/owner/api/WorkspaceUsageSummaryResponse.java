package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record WorkspaceUsageSummaryResponse(
        String workspaceSlug,
        long activeLinksCurrent,
        long membersCurrent,
        long apiKeysCurrent,
        long webhooksCurrent,
        long currentMonthWebhookDeliveries,
        OffsetDateTime currentMonthWindowStart,
        OffsetDateTime currentMonthWindowEnd) {
}
