package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspacePlanRecord(
        long workspaceId,
        WorkspacePlanCode planCode,
        WorkspaceSubscriptionStatus subscriptionStatus,
        int activeLinksLimit,
        int membersLimit,
        int apiKeysLimit,
        int webhooksLimit,
        long monthlyWebhookDeliveriesLimit,
        boolean exportsEnabled,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime graceUntil,
        WorkspacePlanCode scheduledPlanCode,
        OffsetDateTime scheduledPlanEffectiveAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
