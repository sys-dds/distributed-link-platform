package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkMutationResult(
        String slug,
        String originalUrl,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String title,
        List<String> tags,
        String hostname,
        long version,
        boolean deleted) {

    public static LinkMutationResult fromDetails(LinkDetails linkDetails) {
        return new LinkMutationResult(
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.createdAt(),
                linkDetails.expiresAt(),
                linkDetails.title(),
                linkDetails.tags(),
                linkDetails.hostname(),
                linkDetails.version(),
                false);
    }
}
