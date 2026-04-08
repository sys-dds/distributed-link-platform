package com.linkplatform.api.link.api;

public record UpdateWorkspaceAbusePolicyRequest(
        Boolean rawIpReviewEnabled,
        Boolean punycodeReviewEnabled,
        Integer repeatedHostQuarantineThreshold,
        Integer redirectRateLimitQuarantineThreshold) {
}
