package com.linkplatform.api.link.domain;

import java.net.URI;
import java.util.Objects;

public record OriginalUrl(String value) {

    public OriginalUrl {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();

        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            throw new IllegalArgumentException("Original URL must be an absolute URL");
        }

        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Original URL must use http or https");
        }
    }
}
