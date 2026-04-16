package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WorkspaceEnterprisePolicyRecord;
import java.time.OffsetDateTime;

public record WorkspaceEnterprisePolicyResponse(
        boolean requireApiKeyExpiry,
        Integer maxApiKeyTtlDays,
        boolean requireServiceAccountKeyExpiry,
        Integer maxServiceAccountKeyTtlDays,
        boolean requireDualControlForOps,
        boolean requireDualControlForPlanChanges,
        OffsetDateTime updatedAt,
        long updatedByOwnerId) {

    public static WorkspaceEnterprisePolicyResponse from(WorkspaceEnterprisePolicyRecord record) {
        boolean requireApiKeyExpiry = record.requireApiKeyExpiry();
        boolean requireServiceAccountKeyExpiry = record.requireServiceAccountKeyExpiry();
        return new WorkspaceEnterprisePolicyResponse(
                requireApiKeyExpiry,
                record.maxApiKeyTtlDays(),
                requireServiceAccountKeyExpiry,
                record.maxServiceAccountKeyTtlDays(),
                record.requireDualControlForOps(),
                record.requireDualControlForPlanChanges(),
                record.updatedAt(),
                record.updatedByOwnerId());
    }
}
