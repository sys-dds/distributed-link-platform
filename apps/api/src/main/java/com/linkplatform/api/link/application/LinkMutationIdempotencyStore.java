package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface LinkMutationIdempotencyStore {

    Optional<LinkMutationIdempotencyRecord> findByKey(long ownerId, String idempotencyKey);

    void saveResult(
            long ownerId,
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt);
}
