package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkActivityEventResponse(
        String type,
        String slug,
        String originalUrl,
        String title,
        List<String> tags,
        String hostname,
        OffsetDateTime expiresAt,
        OffsetDateTime occurredAt) {
}
