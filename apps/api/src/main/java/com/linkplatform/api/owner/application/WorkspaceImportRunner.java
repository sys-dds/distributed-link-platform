package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceImportRunner {

    private final WorkspaceImportStore workspaceImportStore;
    private final WorkspaceImportService workspaceImportService;
    private final Clock clock;

    public WorkspaceImportRunner(
            WorkspaceImportStore workspaceImportStore,
            WorkspaceImportService workspaceImportService,
            Clock clock) {
        this.workspaceImportStore = workspaceImportStore;
        this.workspaceImportService = workspaceImportService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${link-platform.imports.runner-delay-ms:10000}")
    public void runQueuedImports() {
        OffsetDateTime claimedAt = OffsetDateTime.now(clock);
        workspaceImportStore
                .claimNextQueued(claimedAt)
                .ifPresent(workspaceImportService::processQueuedImport);
    }
}
