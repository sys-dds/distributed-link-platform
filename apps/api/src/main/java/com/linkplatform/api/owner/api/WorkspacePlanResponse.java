package com.linkplatform.api.owner.api;

public record WorkspacePlanResponse(
        String workspaceSlug,
        String planCode,
        int activeLinksLimit,
        int membersLimit,
        int apiKeysLimit,
        int webhooksLimit,
        long monthlyWebhookDeliveriesLimit,
        boolean exportsEnabled) {
}
