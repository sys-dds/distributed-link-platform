package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WorkspaceImportRecord;
import java.time.OffsetDateTime;

public record WorkspaceImportResponse(
        long id,
        Long sourceExportId,
        String status,
        boolean dryRun,
        boolean overwriteConflicts,
        WorkspaceImportSummaryResponse summary,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError) {

    static WorkspaceImportResponse from(WorkspaceImportRecord record) {
        return new WorkspaceImportResponse(
                record.id(),
                record.sourceExportId(),
                record.status(),
                record.dryRun(),
                record.overwriteConflicts(),
                WorkspaceImportSummaryResponse.from(record.summaryJson()),
                record.createdAt(),
                record.startedAt(),
                record.completedAt(),
                record.failedAt(),
                record.lastError());
    }
}
