package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record RedirectClickAnalyticsEvent(
        String eventId,
        String slug,
        Long ownerId,
        OffsetDateTime clickedAt,
        String userAgent,
        String referrer,
        String remoteAddress) {

    public RedirectClickAnalyticsEvent(
            String eventId,
            String slug,
            OffsetDateTime clickedAt,
            String userAgent,
            String referrer,
            String remoteAddress) {
        this(eventId, slug, null, clickedAt, userAgent, referrer, remoteAddress);
    }

    public String eventKey() {
        return slug;
    }
}
