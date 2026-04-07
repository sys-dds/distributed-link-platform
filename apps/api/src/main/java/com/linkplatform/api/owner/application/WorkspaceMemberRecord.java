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
        OffsetDateTime removedAt) {

    public WorkspaceMemberRecord(
            long workspaceId,
            long ownerId,
            WorkspaceRole role,
            OffsetDateTime joinedAt,
            Long addedByOwnerId,
            OffsetDateTime removedAt) {
        this(workspaceId, ownerId, null, null, role, joinedAt, addedByOwnerId, removedAt);
    }
}
