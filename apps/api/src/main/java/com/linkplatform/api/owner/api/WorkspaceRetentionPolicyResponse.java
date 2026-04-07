package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record WorkspaceRetentionPolicyResponse(
        int clickHistoryDays,
        int securityEventsDays,
        int webhookDeliveriesDays,
        int abuseCasesDays,
        int operatorActionLogDays,
        OffsetDateTime updatedAt,
        long updatedByOwnerId) {
}
