package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record WorkspaceMemberResponse(
        long ownerId,
        String ownerKey,
        String displayName,
        String role,
        OffsetDateTime joinedAt) {
}
