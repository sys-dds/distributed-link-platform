package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceExportStore {

    WorkspaceExportRecord create(long workspaceId, long requestedByOwnerId, boolean includeClicks, boolean includeSecurityEvents, boolean includeAbuseCases, boolean includeWebhooks, OffsetDateTime createdAt);

    List<WorkspaceExportRecord> findByWorkspaceId(long workspaceId, int limit);

    Optional<WorkspaceExportRecord> findById(long workspaceId, long exportId);

    Optional<WorkspaceExportRecord> claimNextQueued(OffsetDateTime now);

    void markReady(long exportId, JsonNode payload, long payloadSizeBytes, OffsetDateTime completedAt);

    void markFailed(long exportId, String lastError, OffsetDateTime failedAt);
}
