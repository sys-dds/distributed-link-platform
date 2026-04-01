package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record LinkDetails(String slug, String originalUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
}
