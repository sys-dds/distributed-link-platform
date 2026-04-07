package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record OperatorActionLogRecord(
        long id,
        Long workspaceId,
        long ownerId,
        String subsystem,
        String actionType,
        String targetSlug,
        Long targetCaseId,
        Long targetProjectionJobId,
        String note,
        OffsetDateTime createdAt) {
}
