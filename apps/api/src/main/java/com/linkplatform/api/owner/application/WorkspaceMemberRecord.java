package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceMemberRecord(
        long workspaceId,
        long ownerId,
        String ownerKey,
        String displayName,
        WorkspaceRole role,
        OffsetDateTime joinedAt,
        Long addedByOwnerId,
        OffsetDateTime removedAt,
        String memberType,
        OffsetDateTime suspendedAt,
        Long suspendedByOwnerId,
        String suspendReason) {

    public WorkspaceMemberRecord(
            long workspaceId,
            long ownerId,
            WorkspaceRole role,
            OffsetDateTime joinedAt,
            Long addedByOwnerId,
            OffsetDateTime removedAt) {
        this(workspaceId, ownerId, null, null, role, joinedAt, addedByOwnerId, removedAt, "HUMAN", null, null, null);
    }

    public boolean serviceAccount() {
        return "SERVICE_ACCOUNT".equals(memberType);
    }
}
