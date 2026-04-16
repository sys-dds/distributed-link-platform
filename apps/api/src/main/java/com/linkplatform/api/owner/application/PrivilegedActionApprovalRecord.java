package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record PrivilegedActionApprovalRecord(
        long id,
        long workspaceId,
        String actionType,
        long initiatorOwnerId,
        long approverOwnerId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime consumedAt,
        OffsetDateTime expiresAt) {
}
