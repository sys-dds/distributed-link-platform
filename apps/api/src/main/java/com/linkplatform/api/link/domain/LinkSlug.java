package com.linkplatform.api.link.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record LinkSlug(String value) {

    private static final Pattern VALID_SLUG = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    public LinkSlug {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();

        if (!VALID_SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException("Link slug must be 3-64 characters and use only letters, numbers, hyphens, or underscores");
        }
    }
}
