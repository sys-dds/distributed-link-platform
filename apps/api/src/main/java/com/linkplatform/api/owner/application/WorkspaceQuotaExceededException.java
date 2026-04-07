package com.linkplatform.api.owner.application;

public class WorkspaceQuotaExceededException extends RuntimeException {

    private final WorkspaceUsageMetric quotaMetric;
    private final long currentUsage;
    private final long limit;
    private final String detail;

    public WorkspaceQuotaExceededException(
            WorkspaceUsageMetric quotaMetric,
            long currentUsage,
            long limit,
            String detail) {
        super(detail);
        this.quotaMetric = quotaMetric;
        this.currentUsage = currentUsage;
        this.limit = limit;
        this.detail = detail;
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

    public String detail() {
        return detail;
    }
}
