package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceRecoveryDrillRunner {

    private final WorkspaceRecoveryDrillStore recoveryDrillStore;
    private final WorkspaceRecoveryDrillService recoveryDrillService;
    private final Clock clock;

    public WorkspaceRecoveryDrillRunner(
            WorkspaceRecoveryDrillStore recoveryDrillStore,
            WorkspaceRecoveryDrillService recoveryDrillService,
            Clock clock) {
        this.recoveryDrillStore = recoveryDrillStore;
        this.recoveryDrillService = recoveryDrillService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${link-platform.recovery-drills.runner-delay-ms:10000}")
    public void runQueuedRecoveryDrills() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        recoveryDrillStore.claimNextQueued(now).ifPresent(recoveryDrillService::processQueuedDrill);
    }
}
