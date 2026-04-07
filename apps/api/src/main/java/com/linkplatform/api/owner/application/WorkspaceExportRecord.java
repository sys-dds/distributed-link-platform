package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record WorkspaceExportRecord(
        long id,
        long workspaceId,
        long requestedByOwnerId,
        String status,
        boolean includeClicks,
        boolean includeSecurityEvents,
        boolean includeAbuseCases,
        boolean includeWebhooks,
        JsonNode payload,
        Long payloadSizeBytes,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {
}
