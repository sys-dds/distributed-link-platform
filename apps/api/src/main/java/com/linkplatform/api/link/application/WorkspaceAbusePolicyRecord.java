package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record WorkspaceAbusePolicyRecord(
        long workspaceId,
        boolean rawIpReviewEnabled,
        boolean punycodeReviewEnabled,
        int repeatedHostQuarantineThreshold,
        int redirectRateLimitQuarantineThreshold,
        OffsetDateTime updatedAt,
        Long updatedByOwnerId) {
}
