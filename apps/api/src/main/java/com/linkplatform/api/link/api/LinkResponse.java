package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record LinkResponse(String slug, String originalUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
}
