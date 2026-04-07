package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public record WorkspaceUsageLedgerEntry(
        long id,
        long workspaceId,
        WorkspaceUsageMetric metricCode,
        long quantity,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        String source,
        String sourceRef,
        OffsetDateTime recordedAt) {
}
