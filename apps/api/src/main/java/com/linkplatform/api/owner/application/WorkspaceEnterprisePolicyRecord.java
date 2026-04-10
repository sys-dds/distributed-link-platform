package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceEnterprisePolicyRecord(
        long workspaceId,
        boolean requireApiKeyExpiry,
        Integer maxApiKeyTtlDays,
        boolean requireServiceAccountKeyExpiry,
        Integer maxServiceAccountKeyTtlDays,
        boolean requireDualControlForOps,
        boolean requireDualControlForPlanChanges,
        OffsetDateTime updatedAt,
        long updatedByOwnerId) {
}
