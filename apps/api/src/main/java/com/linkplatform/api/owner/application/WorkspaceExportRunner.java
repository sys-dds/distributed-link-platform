package com.linkplatform.api.owner.application;

import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class WorkspaceExportRunner {

    private final WorkspaceExportStore workspaceExportStore;
    private final WorkspaceExportService workspaceExportService;
    private final Clock clock;

    public WorkspaceExportRunner(
            WorkspaceExportStore workspaceExportStore,
            WorkspaceExportService workspaceExportService) {
        this.workspaceExportStore = workspaceExportStore;
        this.workspaceExportService = workspaceExportService;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${link-platform.exports.runner-delay-ms:10000}")
    public void runQueuedExports() {
        workspaceExportStore.claimNextQueued(OffsetDateTime.now(clock)).ifPresent(workspaceExportService::completeExport);
    }
}
