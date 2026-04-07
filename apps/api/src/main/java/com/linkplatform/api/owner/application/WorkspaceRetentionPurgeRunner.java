package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceRetentionPurgeRunner {

    private final WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;
    private final WebhookDeliveryStore webhookDeliveryStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public WorkspaceRetentionPurgeRunner(
            WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore,
            WebhookDeliveryStore webhookDeliveryStore,
            OperatorActionLogStore operatorActionLogStore,
            SecurityEventStore securityEventStore) {
        this.workspaceRetentionPolicyStore = workspaceRetentionPolicyStore;
        this.webhookDeliveryStore = webhookDeliveryStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public PurgeResult runForWorkspace(WorkspaceAccessContext context) {
        WorkspaceRetentionPolicyRecord policy = workspaceRetentionPolicyStore.findByWorkspaceId(context.workspaceId())
                .orElseThrow(() -> new IllegalArgumentException("Workspace retention policy not found"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        long securityDeleted = workspaceRetentionPolicyStore.purgeSecurityEvents(context.workspaceId(), now.minusDays(policy.securityEventsDays()));
        long operatorDeleted = workspaceRetentionPolicyStore.purgeOperatorActions(context.workspaceId(), now.minusDays(policy.operatorActionLogDays()));
        long webhookDeleted = 0L;
        long abuseDeleted = 0L;
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "workspace_retention_purge",
                null,
                null,
                null,
                "Workspace retention purge executed",
                now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_RETENTION_PURGE_RUN,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/retention/purge",
                null,
                "Workspace retention purge executed",
                now);
        // Click-history purging is intentionally left at zero until it can be done without changing analytics semantics.
        return new PurgeResult(webhookDeleted, securityDeleted, abuseDeleted, operatorDeleted, 0L);
    }

    public record PurgeResult(
            long webhookDeliveriesDeleted,
            long securityEventsDeleted,
            long abuseCasesDeleted,
            long operatorActionsDeleted,
            long clickHistoryDeleted) {
    }
}
