package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceInvitationRecord(
        long id,
        long workspaceId,
        String email,
        WorkspaceRole role,
        String tokenHash,
        String tokenPrefix,
        WorkspaceInvitationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        long createdByOwnerId,
        OffsetDateTime acceptedAt,
        Long acceptedByOwnerId,
        OffsetDateTime revokedAt,
        Long revokedByOwnerId) {
}
