package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record OpsProjectionSummaryResponse(
        long queuedCount,
        long runningCount,
        long failedCount,
        long completedCount,
        OffsetDateTime latestStartedAt,
        OffsetDateTime latestFailedAt) {
}
