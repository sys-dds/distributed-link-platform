package com.linkplatform.api.owner.application;

public class WorkspaceQuotaExceededException extends RuntimeException {

    private final WorkspaceUsageMetric quotaMetric;
    private final long currentUsage;
    private final long limit;

    public WorkspaceQuotaExceededException(
            WorkspaceUsageMetric quotaMetric,
            long currentUsage,
            long limit,
            String message) {
        super(message);
        this.quotaMetric = quotaMetric;
        this.currentUsage = currentUsage;
        this.limit = limit;
    }

    public WorkspaceUsageMetric quotaMetric() {
        return quotaMetric;
    }

    public long currentUsage() {
        return currentUsage;
    }

    public long limit() {
        return limit;
    }
}
