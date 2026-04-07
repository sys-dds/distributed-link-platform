package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceRetentionPolicyRecord(
        long workspaceId,
        int clickHistoryDays,
        int securityEventsDays,
        int webhookDeliveriesDays,
        int abuseCasesDays,
        int operatorActionLogDays,
        OffsetDateTime updatedAt,
        long updatedByOwnerId) {
}
