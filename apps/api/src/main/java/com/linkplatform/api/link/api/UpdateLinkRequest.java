package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record UpdateLinkRequest(String originalUrl, OffsetDateTime expiresAt) {
}
