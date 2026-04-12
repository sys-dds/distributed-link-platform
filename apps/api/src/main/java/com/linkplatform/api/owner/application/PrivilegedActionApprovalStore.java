package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PrivilegedActionApprovalStore {

    PrivilegedActionApprovalRecord createApproved(
            long workspaceId,
            String actionType,
            long initiatorOwnerId,
            long approverOwnerId,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt);

    Optional<PrivilegedActionApprovalRecord> consumeApproved(
            long workspaceId,
            String actionType,
            long initiatorOwnerId,
            OffsetDateTime now);
}
