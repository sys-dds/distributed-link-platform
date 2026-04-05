package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkActivityEvent(
        long ownerId,
        LinkActivityType type,
        String slug,
        String originalUrl,
        String title,
        List<String> tags,
        String hostname,
        OffsetDateTime expiresAt,
        OffsetDateTime occurredAt) {
}
