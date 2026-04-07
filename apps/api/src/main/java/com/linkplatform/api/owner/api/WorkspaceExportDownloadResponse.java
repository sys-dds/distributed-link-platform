package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record WorkspaceExportDownloadResponse(
        long exportId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        JsonNode payload) {
}
