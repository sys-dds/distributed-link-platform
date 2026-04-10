package com.linkplatform.api.owner.application;

import java.util.Optional;

public interface OwnerStore {

    Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash);

    default Optional<AuthenticatedOwner> findById(long ownerId) {
        return Optional.empty();
    }

    default Optional<AuthenticatedOwner> findByOwnerKeyIgnoreCase(String ownerKey) {
        return Optional.empty();
    }

    void lockById(long ownerId);
}
