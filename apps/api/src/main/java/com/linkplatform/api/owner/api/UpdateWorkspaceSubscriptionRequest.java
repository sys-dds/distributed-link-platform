package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record UpdateWorkspaceSubscriptionRequest(
        String subscriptionStatus,
        OffsetDateTime graceUntil,
        String scheduledPlanCode,
        OffsetDateTime scheduledPlanEffectiveAt) {
}
