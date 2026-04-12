package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface OperatorActionLogStore {

    default void recordWorkspaceSubscriptionChange(
            Long workspaceId,
            long ownerId,
            String actionType,
            String workspaceSlug,
            String note,
            OffsetDateTime createdAt) {
        record(workspaceId, ownerId, "PIPELINE", actionType, workspaceSlug, null, null, note, createdAt);
    }

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

    default void recordWorkspaceRecoveryDrill(
            long workspaceId,
            long ownerId,
            String note,
            OffsetDateTime createdAt) {
        record(workspaceId, ownerId, "PIPELINE", "workspace_recovery_drill_request", null, null, null, note, createdAt);
    }

    List<OperatorActionLogRecord> findRecent(Long workspaceId, OperatorActionLogQuery query);

    default boolean supportsGlobalGovernanceViews() {
        return true;
    }
}
