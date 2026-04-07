package com.linkplatform.api.owner.api;

public record WorkspaceRetentionPurgeResponse(
        long webhookDeliveriesDeleted,
        long securityEventsDeleted,
        long abuseCasesDeleted,
        long operatorActionsDeleted,
        long clickHistoryDeleted) {
}
