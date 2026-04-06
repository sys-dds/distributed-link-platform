package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkActivityEvent;
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

    public static LinkActivityEventResponse from(LinkActivityEvent linkActivityEvent) {
        return new LinkActivityEventResponse(
                linkActivityEvent.type().name().toLowerCase(),
                linkActivityEvent.slug(),
                linkActivityEvent.originalUrl(),
                linkActivityEvent.title(),
                linkActivityEvent.tags(),
                linkActivityEvent.hostname(),
                linkActivityEvent.expiresAt(),
                linkActivityEvent.occurredAt());
    }
}
