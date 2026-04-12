package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public interface WorkspaceEnterprisePolicyStore {

    // Enterprise policy settings remain separate from durable privileged-action approvals.
    WorkspaceEnterprisePolicyRecord findOrCreateDefault(long workspaceId, long ownerId, OffsetDateTime now);

    WorkspaceEnterprisePolicyRecord update(
            long workspaceId,
            boolean requireApiKeyExpiry,
            Integer maxApiKeyTtlDays,
            boolean requireServiceAccountKeyExpiry,
            Integer maxServiceAccountKeyTtlDays,
            boolean requireDualControlForOps,
            boolean requireDualControlForPlanChanges,
            OffsetDateTime updatedAt,
            long updatedByOwnerId);
}
