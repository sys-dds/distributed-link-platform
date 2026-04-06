package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record OwnerApiKeyRecord(
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

    public boolean activeAt(OffsetDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
