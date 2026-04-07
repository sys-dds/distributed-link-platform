package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceRecord(
        long id,
        String slug,
        String displayName,
        boolean personalWorkspace,
        OffsetDateTime createdAt,
        long createdByOwnerId,
        OffsetDateTime archivedAt,
        WorkspaceRole callerRole) {

    public WorkspaceRecord(
            long id,
            String slug,
            String displayName,
            boolean personalWorkspace,
            OffsetDateTime createdAt,
            long createdByOwnerId,
            OffsetDateTime archivedAt) {
        this(id, slug, displayName, personalWorkspace, createdAt, createdByOwnerId, archivedAt, null);
    }
}
