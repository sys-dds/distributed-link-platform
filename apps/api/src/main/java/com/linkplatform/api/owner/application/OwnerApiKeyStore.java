package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OwnerApiKeyStore {

    OwnerApiKeyRecord create(
            long ownerId,
            String keyPrefix,
            String keyHash,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String createdBy);

    List<OwnerApiKeyRecord> findByOwnerId(long ownerId);

    List<OwnerApiKeyRecord> findActiveByOwnerId(long ownerId, OffsetDateTime now);

    Optional<OwnerApiKeyRecord> findById(long ownerId, long keyId);

    Optional<OwnerApiKeyRecord> findActiveByHash(String keyHash, OffsetDateTime now);

    void revoke(long ownerId, long keyId, OffsetDateTime revokedAt, String revokedBy);

    void expire(long ownerId, long keyId, OffsetDateTime expiresAt, String revokedBy);

    void touchLastUsed(long keyId, OffsetDateTime lastUsedAt);

    void lockOwner(long ownerId);
}
