package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkDiscoveryItem(
        String slug,
        String originalUrl,
        String title,
        String hostname,
        List<String> tags,
        LinkAbuseStatus abuseStatus,
        LinkDiscoveryLifecycleState lifecycleState,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime deletedAt,
        long version) {
}
