package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record WorkspaceImportRecord(
        long id,
        long workspaceId,
        long requestedByOwnerId,
        Long sourceExportId,
        String status,
        boolean dryRun,
        boolean overwriteConflicts,
        JsonNode payloadJson,
        JsonNode summaryJson,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {
}
