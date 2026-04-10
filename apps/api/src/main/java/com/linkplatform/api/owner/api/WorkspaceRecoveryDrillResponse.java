package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkplatform.api.owner.application.WorkspaceRecoveryDrillRecord;
import java.time.OffsetDateTime;

public record WorkspaceRecoveryDrillResponse(
        long id,
        long sourceExportId,
        String status,
        boolean dryRun,
        String targetMode,
        JsonNode summary,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {

    public static WorkspaceRecoveryDrillResponse from(WorkspaceRecoveryDrillRecord record) {
        return new WorkspaceRecoveryDrillResponse(
                record.id(),
                record.sourceExportId(),
                record.status(),
                record.dryRun(),
                record.targetMode(),
                record.summaryJson(),
                record.createdAt(),
                record.startedAt(),
                record.completedAt(),
                record.failedAt(),
                record.lastError());
    }
}
