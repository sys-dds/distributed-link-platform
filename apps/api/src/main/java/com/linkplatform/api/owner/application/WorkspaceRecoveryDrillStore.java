package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRecoveryDrillStore {

    WorkspaceRecoveryDrillRecord create(
            long workspaceId,
            long requestedByOwnerId,
            long sourceExportId,
            boolean dryRun,
            String targetMode,
            OffsetDateTime createdAt);

    List<WorkspaceRecoveryDrillRecord> findByWorkspaceId(long workspaceId, int limit);

    Optional<WorkspaceRecoveryDrillRecord> findById(long workspaceId, long drillId);

    Optional<WorkspaceRecoveryDrillRecord> claimNextQueued(OffsetDateTime startedAt);

    void markCompleted(long drillId, JsonNode summaryJson, OffsetDateTime completedAt);

    void markFailed(long drillId, String lastError, OffsetDateTime failedAt);
}
