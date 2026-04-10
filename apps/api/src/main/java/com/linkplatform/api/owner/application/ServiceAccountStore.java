package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceAccountStore {

    long createServiceAccountOwner(String ownerKey, String displayName, OffsetDateTime createdAt);

    ServiceAccountRecord create(
            long serviceAccountId,
            long workspaceId,
            String name,
            String slug,
            ServiceAccountStatus status,
            OffsetDateTime createdAt,
            long createdByOwnerId);

    Optional<ServiceAccountRecord> findById(long serviceAccountId);

    Optional<ServiceAccountRecord> findByWorkspaceIdAndId(long workspaceId, long serviceAccountId);

    Optional<ServiceAccountRecord> findByWorkspaceIdAndSlug(long workspaceId, String slug);

    List<ServiceAccountRecord> findByWorkspaceId(long workspaceId);

    boolean disable(long serviceAccountId, OffsetDateTime disabledAt, long disabledByOwnerId);
}
