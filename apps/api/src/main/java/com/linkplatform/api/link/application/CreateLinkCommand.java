package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record CreateLinkCommand(
        String slug,
        String originalUrl,
        OffsetDateTime expiresAt,
        String title,
        List<String> tags) {

    public CreateLinkCommand {
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(originalUrl, "originalUrl must not be null");
    }
}
