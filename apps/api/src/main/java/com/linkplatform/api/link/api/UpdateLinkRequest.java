package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record UpdateLinkRequest(String originalUrl, OffsetDateTime expiresAt, String title, List<String> tags) {
}
