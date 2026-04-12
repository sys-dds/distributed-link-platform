package com.linkplatform.api.owner.api;

public record UpdateWorkspaceEnterprisePolicyRequest(
        Boolean requireApiKeyExpiry,
        Integer maxApiKeyTtlDays,
        Boolean requireServiceAccountKeyExpiry,
        Integer maxServiceAccountKeyTtlDays,
        Boolean requireDualControlForOps,
        Boolean requireDualControlForPlanChanges) {
}
