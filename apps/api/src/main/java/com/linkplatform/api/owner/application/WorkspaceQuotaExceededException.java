package com.linkplatform.api.owner.application;

import java.util.Objects;

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
        super(Objects.requireNonNull(detail, "detail must not be null"));
        this.quotaMetric = Objects.requireNonNull(quotaMetric, "quotaMetric must not be null");
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
