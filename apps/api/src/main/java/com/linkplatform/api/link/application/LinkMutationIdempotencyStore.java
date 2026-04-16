package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface LinkMutationIdempotencyStore {

    default void lockKey(long ownerId, String idempotencyKey) {
    }

    Optional<LinkMutationIdempotencyRecord> findByKey(long ownerId, String idempotencyKey);

    void saveResult(
            long ownerId,
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt);
}
