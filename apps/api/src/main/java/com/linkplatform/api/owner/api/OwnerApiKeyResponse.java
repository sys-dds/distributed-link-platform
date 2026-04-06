package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record OwnerApiKeyResponse(
        long id,
        String keyPrefix,
        String label,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime expiresAt,
        boolean active) {
}
