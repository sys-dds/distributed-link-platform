package com.linkplatform.api.link.application;

import java.util.Objects;

public record CreateLinkCommand(String slug, String originalUrl) {

    public CreateLinkCommand {
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(originalUrl, "originalUrl must not be null");
    }
}
