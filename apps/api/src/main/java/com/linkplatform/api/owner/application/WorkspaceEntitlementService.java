package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceEntitlementService {

    private final WorkspacePlanStore workspacePlanStore;
    private final WorkspaceUsageStore workspaceUsageStore;
    private final WorkspaceStore workspaceStore;
    private final OwnerApiKeyStore ownerApiKeyStore;
    private final Clock clock;

    public WorkspaceEntitlementService(
            WorkspacePlanStore workspacePlanStore,
            WorkspaceUsageStore workspaceUsageStore,
            WorkspaceStore workspaceStore,
            OwnerApiKeyStore ownerApiKeyStore) {
        this.workspacePlanStore = workspacePlanStore;
        this.workspaceUsageStore = workspaceUsageStore;
        this.workspaceStore = workspaceStore;
        this.ownerApiKeyStore = ownerApiKeyStore;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public WorkspacePlanRecord currentPlan(long workspaceId) {
        return workspacePlanStore.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace plan not found for workspace " + workspaceId));
    }

    @Transactional(readOnly = true)
    public UsageSummary currentUsage(long workspaceId) {
        OffsetDateTime windowStart = currentMonthWindowStart();
        OffsetDateTime windowEnd = nextMonthWindowStart(windowStart);
        return new UsageSummary(
                workspaceId,
                workspaceUsageStore.currentSnapshot(workspaceId, WorkspaceUsageMetric.ACTIVE_LINKS),
                workspaceUsageStore.currentSnapshot(workspaceId, WorkspaceUsageMetric.MEMBERS),
                workspaceUsageStore.currentSnapshot(workspaceId, WorkspaceUsageMetric.API_KEYS),
                workspaceUsageStore.currentSnapshot(workspaceId, WorkspaceUsageMetric.WEBHOOKS),
                workspaceUsageStore.sumInWindow(workspaceId, WorkspaceUsageMetric.WEBHOOK_DELIVERIES, windowStart, windowEnd),
                windowStart,
                windowEnd);
    }

    @Transactional
    public WorkspacePlanRecord updatePlan(long workspaceId, WorkspacePlanCode planCode, OffsetDateTime updatedAt) {
        return workspacePlanStore.upsertPlan(workspaceId, planCode, updatedAt);
    }

    @Transactional
    public void enforceMembersQuota(long workspaceId) {
        WorkspacePlanRecord plan = currentPlan(workspaceId);
        long current = currentMembersCount(workspaceId);
        if (current >= plan.membersLimit()) {
            throw exceeded(WorkspaceUsageMetric.MEMBERS, current, plan.membersLimit(),
                    "Workspace member quota exceeded");
        }
    }

    @Transactional
    public void enforceApiKeysQuota(long workspaceId, OffsetDateTime now) {
        WorkspacePlanRecord plan = currentPlan(workspaceId);
        long current = currentActiveApiKeysCount(workspaceId, now);
        if (current >= plan.apiKeysLimit()) {
            throw exceeded(WorkspaceUsageMetric.API_KEYS, current, plan.apiKeysLimit(),
                    "Workspace API key quota exceeded");
        }
    }

    @Transactional
    public void enforceActiveLinksQuota(long workspaceId, long currentActiveLinks) {
        WorkspacePlanRecord plan = currentPlan(workspaceId);
        if (currentActiveLinks >= plan.activeLinksLimit()) {
            throw exceeded(WorkspaceUsageMetric.ACTIVE_LINKS, currentActiveLinks, plan.activeLinksLimit(),
                    "Workspace active link quota exceeded");
        }
    }

    @Transactional
    public void enforceWebhooksQuota(long workspaceId, long currentWebhooks) {
        WorkspacePlanRecord plan = currentPlan(workspaceId);
        if (currentWebhooks >= plan.webhooksLimit()) {
            throw exceeded(WorkspaceUsageMetric.WEBHOOKS, currentWebhooks, plan.webhooksLimit(),
                    "Workspace webhook quota exceeded");
        }
    }

    @Transactional(readOnly = true)
    public boolean exportsEnabled(long workspaceId) {
        return currentPlan(workspaceId).exportsEnabled();
    }

    @Transactional
    public void enforceMonthlyWebhookDeliveryQuota(long workspaceId, long additionalAttempts) {
        WorkspacePlanRecord plan = currentPlan(workspaceId);
        OffsetDateTime windowStart = currentMonthWindowStart();
        OffsetDateTime windowEnd = nextMonthWindowStart(windowStart);
        long used = workspaceUsageStore.sumInWindow(workspaceId, WorkspaceUsageMetric.WEBHOOK_DELIVERIES, windowStart, windowEnd);
        if (used + additionalAttempts > plan.monthlyWebhookDeliveriesLimit()) {
            throw exceeded(
                    WorkspaceUsageMetric.WEBHOOK_DELIVERIES,
                    used,
                    plan.monthlyWebhookDeliveriesLimit(),
                    "Workspace monthly webhook delivery quota exceeded");
        }
    }

    @Transactional
    public void recordActiveLinksSnapshot(long workspaceId, long quantity, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordSnapshot(workspaceId, WorkspaceUsageMetric.ACTIVE_LINKS, quantity, source, sourceRef, recordedAt);
    }

    @Transactional
    public void recordMembersSnapshot(long workspaceId, long quantity, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordSnapshot(workspaceId, WorkspaceUsageMetric.MEMBERS, quantity, source, sourceRef, recordedAt);
    }

    @Transactional
    public void recordApiKeysSnapshot(long workspaceId, long quantity, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordSnapshot(workspaceId, WorkspaceUsageMetric.API_KEYS, quantity, source, sourceRef, recordedAt);
    }

    @Transactional
    public void recordWebhooksSnapshot(long workspaceId, long quantity, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordSnapshot(workspaceId, WorkspaceUsageMetric.WEBHOOKS, quantity, source, sourceRef, recordedAt);
    }

    @Transactional
    public void recordWebhookDeliveryUsage(long workspaceId, long quantity, String source, String sourceRef, OffsetDateTime recordedAt) {
        OffsetDateTime windowStart = currentMonthWindowStart();
        OffsetDateTime windowEnd = nextMonthWindowStart(windowStart);
        workspaceUsageStore.recordAdditive(
                workspaceId,
                WorkspaceUsageMetric.WEBHOOK_DELIVERIES,
                quantity,
                windowStart,
                windowEnd,
                source,
                sourceRef,
                recordedAt);
    }

    @Transactional(readOnly = true)
    public long currentMembersCount(long workspaceId) {
        return workspaceStore.findActiveMembers(workspaceId).size();
    }

    @Transactional(readOnly = true)
    public long currentActiveApiKeysCount(long workspaceId, OffsetDateTime now) {
        return ownerApiKeyStore.findActiveByWorkspaceId(workspaceId, now).size();
    }

    @Transactional
    public void recordCurrentMembersSnapshot(long workspaceId, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordMembersSnapshot(workspaceId, currentMembersCount(workspaceId), source, sourceRef, recordedAt);
    }

    @Transactional
    public void recordCurrentApiKeysSnapshot(long workspaceId, String source, String sourceRef, OffsetDateTime recordedAt) {
        recordApiKeysSnapshot(workspaceId, currentActiveApiKeysCount(workspaceId, recordedAt), source, sourceRef, recordedAt);
    }

    private void recordSnapshot(
            long workspaceId,
            WorkspaceUsageMetric metric,
            long quantity,
            String source,
            String sourceRef,
            OffsetDateTime recordedAt) {
        workspaceUsageStore.recordSnapshot(workspaceId, metric, quantity, source, sourceRef, recordedAt);
    }

    private OffsetDateTime currentMonthWindowStart() {
        YearMonth yearMonth = YearMonth.now(clock);
        return yearMonth.atDay(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
    }

    private OffsetDateTime nextMonthWindowStart(OffsetDateTime windowStart) {
        return windowStart.plusMonths(1);
    }

    private WorkspaceQuotaExceededException exceeded(WorkspaceUsageMetric metric, long currentUsage, long limit, String detail) {
        return new WorkspaceQuotaExceededException(metric, currentUsage, limit, detail);
    }

    public record UsageSummary(
            long workspaceId,
            long activeLinksCurrent,
            long membersCurrent,
            long apiKeysCurrent,
            long webhooksCurrent,
            long currentMonthWebhookDeliveries,
            OffsetDateTime currentMonthWindowStart,
            OffsetDateTime currentMonthWindowEnd) {
    }
}
