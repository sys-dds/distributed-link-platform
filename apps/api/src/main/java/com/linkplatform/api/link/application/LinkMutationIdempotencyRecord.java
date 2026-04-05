package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record LinkMutationIdempotencyRecord(
        long ownerId,
        String idempotencyKey,
        String operation,
        String requestHash,
        LinkMutationResult result,
        OffsetDateTime createdAt) {
}
