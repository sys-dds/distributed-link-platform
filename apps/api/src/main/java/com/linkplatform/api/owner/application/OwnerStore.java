package com.linkplatform.api.owner.application;

import java.util.Optional;

public interface OwnerStore {

    Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash);

    void lockById(long ownerId);
}
