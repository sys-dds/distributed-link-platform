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

    public static WorkspaceQuotaExceededException members(long currentUsage, long limit) {
        return new WorkspaceQuotaExceededException(
                WorkspaceUsageMetric.MEMBERS,
                currentUsage,
                limit,
                "Workspace member quota exceeded");
    }

    public static WorkspaceQuotaExceededException apiKeys(long currentUsage, long limit) {
        return new WorkspaceQuotaExceededException(
                WorkspaceUsageMetric.API_KEYS,
                currentUsage,
                limit,
                "Workspace API key quota exceeded");
    }

    public static WorkspaceQuotaExceededException activeLinks(long currentUsage, long limit) {
        return new WorkspaceQuotaExceededException(
                WorkspaceUsageMetric.ACTIVE_LINKS,
                currentUsage,
                limit,
                "Workspace active link quota exceeded");
    }

    public static WorkspaceQuotaExceededException webhooks(long currentUsage, long limit) {
        return new WorkspaceQuotaExceededException(
                WorkspaceUsageMetric.WEBHOOKS,
                currentUsage,
                limit,
                "Workspace webhook quota exceeded");
    }

    public static WorkspaceQuotaExceededException monthlyWebhookDeliveries(long currentUsage, long limit) {
        return new WorkspaceQuotaExceededException(
                WorkspaceUsageMetric.WEBHOOK_DELIVERIES,
                currentUsage,
                limit,
                "Workspace monthly webhook delivery quota exceeded");
    }
}
