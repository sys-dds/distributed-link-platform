package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Set;

public record OwnerApiKeyRecord(
        long id,
        long ownerId,
        String ownerKey,
        OwnerPlan ownerPlan,
        long workspaceId,
        String workspaceSlug,
        String keyPrefix,
        String keyHash,
        String label,
        Set<ApiKeyScope> scopes,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime expiresAt,
        String createdBy,
        String revokedBy) {

    public OwnerApiKeyRecord(
            long id,
            long ownerId,
            String ownerKey,
            OwnerPlan ownerPlan,
            String keyPrefix,
            String keyHash,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime lastUsedAt,
            OffsetDateTime revokedAt,
            OffsetDateTime expiresAt,
            String createdBy,
            String revokedBy) {
        this(
                id,
                ownerId,
                ownerKey,
                ownerPlan,
                ownerId,
                ownerKey,
                keyPrefix,
                keyHash,
                label,
                WorkspaceRole.OWNER.impliedScopes(),
                createdAt,
                lastUsedAt,
                revokedAt,
                expiresAt,
                createdBy,
                revokedBy);
    }

    public boolean activeAt(OffsetDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
