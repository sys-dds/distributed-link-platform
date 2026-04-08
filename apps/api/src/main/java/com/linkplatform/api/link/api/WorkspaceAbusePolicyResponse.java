package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.WorkspaceAbusePolicyRecord;
import java.time.OffsetDateTime;

public record WorkspaceAbusePolicyResponse(
        boolean rawIpReviewEnabled,
        boolean punycodeReviewEnabled,
        int repeatedHostQuarantineThreshold,
        int redirectRateLimitQuarantineThreshold,
        OffsetDateTime updatedAt,
        Long updatedByOwnerId) {

    static WorkspaceAbusePolicyResponse from(WorkspaceAbusePolicyRecord record) {
        return new WorkspaceAbusePolicyResponse(
                record.rawIpReviewEnabled(),
                record.punycodeReviewEnabled(),
                record.repeatedHostQuarantineThreshold(),
                record.redirectRateLimitQuarantineThreshold(),
                record.updatedAt(),
                record.updatedByOwnerId());
    }
}
