package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record LinkReadResponse(
        String slug,
        String originalUrl,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        long clickTotal) {
}
