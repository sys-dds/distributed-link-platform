package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkLifecycleEvent(
        String eventId,
        LinkLifecycleEventType eventType,
        long ownerId,
        String slug,
        String originalUrl,
        String title,
        List<String> tags,
        String hostname,
        OffsetDateTime expiresAt,
        long version,
        OffsetDateTime occurredAt) {

    public String eventKey() {
        return slug;
    }
}
