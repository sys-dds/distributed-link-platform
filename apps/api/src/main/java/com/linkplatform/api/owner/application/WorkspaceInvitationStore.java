package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationStore {

    WorkspaceInvitationRecord create(
            long workspaceId,
            String email,
            WorkspaceRole role,
            String tokenHash,
            String tokenPrefix,
            WorkspaceInvitationStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            long createdByOwnerId);

    Optional<WorkspaceInvitationRecord> findById(long invitationId);

    Optional<WorkspaceInvitationRecord> findPendingByTokenHash(String tokenHash);

    List<WorkspaceInvitationRecord> findByWorkspaceId(long workspaceId);

    boolean markAccepted(long invitationId, OffsetDateTime acceptedAt, long acceptedByOwnerId);

    boolean markRevoked(long invitationId, OffsetDateTime revokedAt, long revokedByOwnerId);

    boolean markExpired(long invitationId);
}
