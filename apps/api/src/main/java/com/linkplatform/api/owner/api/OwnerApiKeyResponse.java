package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;
import java.util.List;

public record OwnerApiKeyResponse(
        long id,
        String workspaceSlug,
        String keyPrefix,
        String label,
        List<String> scopes,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime expiresAt,
        boolean active) {
}
