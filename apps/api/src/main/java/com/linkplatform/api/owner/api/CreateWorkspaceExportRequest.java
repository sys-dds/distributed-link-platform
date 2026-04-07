package com.linkplatform.api.owner.api;

public record CreateWorkspaceExportRequest(
        Boolean includeClicks,
        Boolean includeSecurityEvents,
        Boolean includeAbuseCases,
        Boolean includeWebhooks) {
}
