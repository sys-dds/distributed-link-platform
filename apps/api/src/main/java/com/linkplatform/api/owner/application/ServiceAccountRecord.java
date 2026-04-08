package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record ServiceAccountRecord(
        long id,
        long workspaceId,
        String name,
        String slug,
        ServiceAccountStatus status,
        OffsetDateTime createdAt,
        long createdByOwnerId,
        OffsetDateTime disabledAt,
        Long disabledByOwnerId) {
}
