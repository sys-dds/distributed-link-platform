package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface OperatorActionLogStore {

    void record(
            Long workspaceId,
            long ownerId,
            String subsystem,
            String actionType,
            String targetSlug,
            Long targetCaseId,
            Long targetProjectionJobId,
            String note,
            OffsetDateTime createdAt);

    List<OperatorActionLogRecord> findRecent(Long workspaceId, OperatorActionLogQuery query);
}
