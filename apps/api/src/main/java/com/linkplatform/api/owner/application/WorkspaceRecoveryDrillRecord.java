package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record WorkspaceRecoveryDrillRecord(
        long id,
        long workspaceId,
        long requestedByOwnerId,
        long sourceExportId,
        String status,
        boolean dryRun,
        String targetMode,
        JsonNode summaryJson,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {
}
