package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record ServiceAccountResponse(
        long id,
        String name,
        String slug,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime disabledAt) {
}
