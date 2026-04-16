package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface WorkspaceUsageStore {

    void lockWorkspaceForQuota(long workspaceId);

    WorkspaceUsageLedgerEntry recordSnapshot(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt);

    WorkspaceUsageLedgerEntry recordAdditive(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt);

    long currentSnapshot(long workspaceId, WorkspaceUsageMetric metric);

    long sumInWindow(long workspaceId, WorkspaceUsageMetric metric, OffsetDateTime windowStart, OffsetDateTime windowEnd);

    List<WorkspaceUsageLedgerEntry> findRecent(long workspaceId, WorkspaceUsageMetric metric, int limit);
}
