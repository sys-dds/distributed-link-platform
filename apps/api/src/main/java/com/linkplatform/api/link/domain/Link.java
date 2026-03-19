package com.linkplatform.api.link.domain;

import java.util.Objects;

public record Link(LinkSlug slug, OriginalUrl originalUrl) {

    public Link {
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(originalUrl, "originalUrl must not be null");
    }
}
