package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkDiscoveryItemResponse(
        String slug,
        String originalUrl,
        String title,
        String hostname,
        List<String> tags,
        String abuseStatus,
        String lifecycleState,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime deletedAt,
        long version) {
}
