package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record BulkLinkActionRequest(
        String action,
        List<String> slugs,
        List<String> tags,
        OffsetDateTime expiresAt) {
}
