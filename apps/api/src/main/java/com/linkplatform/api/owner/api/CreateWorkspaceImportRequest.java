package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateWorkspaceImportRequest(
        Long sourceExportId,
        JsonNode payloadJson,
        Boolean dryRun,
        Boolean overwriteConflicts) {
}
