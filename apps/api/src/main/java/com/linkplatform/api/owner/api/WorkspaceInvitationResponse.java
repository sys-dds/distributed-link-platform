package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record WorkspaceInvitationResponse(
        long id,
        String email,
        String role,
        String status,
        String tokenPrefix,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime revokedAt) {
}
