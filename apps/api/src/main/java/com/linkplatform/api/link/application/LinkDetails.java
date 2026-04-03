package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkDetails(
        String slug,
        String originalUrl,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String title,
        List<String> tags,
        String hostname,
        long clickTotal) {
}
