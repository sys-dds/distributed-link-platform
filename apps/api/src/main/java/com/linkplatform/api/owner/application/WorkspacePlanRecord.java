package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspacePlanRecord(
        long workspaceId,
        WorkspacePlanCode planCode,
        int activeLinksLimit,
        int membersLimit,
        int apiKeysLimit,
        int webhooksLimit,
        long monthlyWebhookDeliveriesLimit,
        boolean exportsEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
