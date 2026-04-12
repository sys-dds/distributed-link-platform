package com.linkplatform.api.owner.api;

public record CreateWorkspaceRecoveryDrillRequest(
        long sourceExportId,
        String targetMode,
        Boolean dryRun) {
}
