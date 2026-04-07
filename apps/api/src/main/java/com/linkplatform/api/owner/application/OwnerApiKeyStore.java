package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OwnerApiKeyStore {

    default OwnerApiKeyRecord create(
            long ownerId,
            long workspaceId,
            String keyPrefix,
            String keyHash,
            String label,
            Set<ApiKeyScope> scopes,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String createdBy) {
        return create(ownerId, keyPrefix, keyHash, label, createdAt, expiresAt, createdBy);
    }

    default OwnerApiKeyRecord create(
            long ownerId,
            String keyPrefix,
            String keyHash,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String createdBy) {
        throw new UnsupportedOperationException("Legacy owner-scoped key creation is not implemented");
    }

    default List<OwnerApiKeyRecord> findByWorkspaceId(long workspaceId) {
        return findByOwnerId(workspaceId);
    }

    default List<OwnerApiKeyRecord> findByOwnerId(long ownerId) {
        throw new UnsupportedOperationException("Legacy owner-scoped key lookup is not implemented");
    }

    default List<OwnerApiKeyRecord> findActiveByWorkspaceId(long workspaceId, OffsetDateTime now) {
        return findActiveByOwnerId(workspaceId, now);
    }

    default List<OwnerApiKeyRecord> findActiveByOwnerId(long ownerId, OffsetDateTime now) {
        throw new UnsupportedOperationException("Legacy owner-scoped active key lookup is not implemented");
    }

    Optional<OwnerApiKeyRecord> findById(long workspaceId, long keyId);

    Optional<OwnerApiKeyRecord> findActiveByHash(String keyHash, OffsetDateTime now);

    void revoke(long workspaceId, long keyId, OffsetDateTime revokedAt, String revokedBy);

    void expire(long workspaceId, long keyId, OffsetDateTime expiresAt, String revokedBy);

    void touchLastUsed(long keyId, OffsetDateTime lastUsedAt);

    default void lockWorkspace(long workspaceId) {
        lockOwner(workspaceId);
    }

    default void lockOwner(long ownerId) {
    }
}
