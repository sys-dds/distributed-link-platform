package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record CreateLinkRequest(String slug, String originalUrl, OffsetDateTime expiresAt) {
}
