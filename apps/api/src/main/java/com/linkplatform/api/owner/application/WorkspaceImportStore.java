package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceImportStore {

    WorkspaceImportRecord create(
            long workspaceId,
            long requestedByOwnerId,
            Long sourceExportId,
            boolean dryRun,
            boolean overwriteConflicts,
            JsonNode payloadJson,
            OffsetDateTime createdAt);

    List<WorkspaceImportRecord> findByWorkspaceId(long workspaceId, int limit);

    Optional<WorkspaceImportRecord> findById(long workspaceId, long importId);

    Optional<WorkspaceImportRecord> claimNextQueued(OffsetDateTime now);

    void markReadyToApply(long importId, JsonNode summaryJson, OffsetDateTime completedAt);

    void markCompleted(long importId, JsonNode summaryJson, OffsetDateTime completedAt);

    void markFailed(long importId, String lastError, OffsetDateTime failedAt);
}
