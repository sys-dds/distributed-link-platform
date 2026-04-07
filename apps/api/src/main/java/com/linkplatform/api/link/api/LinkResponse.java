package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkResponse(
        String slug,
        String originalUrl,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String title,
        List<String> tags,
        String hostname,
        String abuseStatus,
        long version) {

    public LinkResponse(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long version) {
        this(slug, originalUrl, createdAt, expiresAt, title, tags, hostname, null, version);
    }
}
