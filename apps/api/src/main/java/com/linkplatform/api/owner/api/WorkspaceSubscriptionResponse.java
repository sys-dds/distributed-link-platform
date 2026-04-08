package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record WorkspaceSubscriptionResponse(
        String workspaceSlug,
        String planCode,
        String subscriptionStatus,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime graceUntil,
        String scheduledPlanCode,
        OffsetDateTime scheduledPlanEffectiveAt) {
}
