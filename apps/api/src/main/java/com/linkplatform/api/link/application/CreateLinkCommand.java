package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.Objects;

public record CreateLinkCommand(String slug, String originalUrl, OffsetDateTime expiresAt) {

    public CreateLinkCommand {
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(originalUrl, "originalUrl must not be null");
    }
}
