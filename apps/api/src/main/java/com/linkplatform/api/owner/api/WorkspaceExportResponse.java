package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WorkspaceExportRecord;
import java.time.OffsetDateTime;

public record WorkspaceExportResponse(
        long id,
        String status,
        boolean includeClicks,
        boolean includeSecurityEvents,
        boolean includeAbuseCases,
        boolean includeWebhooks,
        Long payloadSizeBytes,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {

    static WorkspaceExportResponse from(WorkspaceExportRecord record) {
        return new WorkspaceExportResponse(
                record.id(),
                record.status(),
                record.includeClicks(),
                record.includeSecurityEvents(),
                record.includeAbuseCases(),
                record.includeWebhooks(),
                record.payloadSizeBytes(),
                record.createdAt(),
                record.startedAt(),
                record.completedAt(),
                record.failedAt(),
                record.lastError());
    }
}
