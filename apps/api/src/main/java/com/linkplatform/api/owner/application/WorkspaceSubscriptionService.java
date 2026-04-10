package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSubscriptionService {

    private final WorkspacePlanStore workspacePlanStore;
    private final SecurityEventStore securityEventStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final Clock clock;

    public WorkspaceSubscriptionService(
            WorkspacePlanStore workspacePlanStore,
            SecurityEventStore securityEventStore,
            OperatorActionLogStore operatorActionLogStore,
            Clock clock) {
        this.workspacePlanStore = workspacePlanStore;
        this.securityEventStore = securityEventStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public WorkspacePlanRecord updateSubscription(
            long workspaceId,
            String workspaceSlug,
            WorkspaceSubscriptionStatus subscriptionStatus,
            OffsetDateTime graceUntil,
            WorkspacePlanCode scheduledPlanCode,
            OffsetDateTime scheduledPlanEffectiveAt,
            Long operatorOwnerId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        validateRequest(subscriptionStatus, graceUntil, scheduledPlanCode, scheduledPlanEffectiveAt);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspacePlanRecord updated = workspacePlanStore.updateSubscriptionLifecycle(
                workspaceId,
                subscriptionStatus,
                graceUntil,
                scheduledPlanCode,
                scheduledPlanEffectiveAt,
                now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_SUBSCRIPTION_STATE_CHANGED,
                operatorOwnerId,
                workspaceId,
                apiKeyHash,
                requestMethod,
                requestPath,
                remoteAddress,
                "Workspace subscription state changed",
                now);
        operatorActionLogStore.record(
                workspaceId,
                operatorOwnerId,
                "PIPELINE",
                "workspace_subscription_update",
                workspaceSlug,
                null,
                null,
                "Subscription set to " + updated.subscriptionStatus().name(),
                now);
        if (updated.scheduledPlanCode() != null && updated.scheduledPlanEffectiveAt() != null) {
            recordScheduledPlanChange(
                    updated,
                    workspaceId,
                    workspaceSlug,
                    operatorOwnerId,
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    now);
        }
        return updated;
    }

    private void recordScheduledPlanChange(
            WorkspacePlanRecord updated,
            long workspaceId,
            String workspaceSlug,
            Long operatorOwnerId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_PLAN_CHANGE_SCHEDULED,
                operatorOwnerId,
                workspaceId,
                apiKeyHash,
                requestMethod,
                requestPath,
                remoteAddress,
                "Workspace plan change scheduled",
                now);
        operatorActionLogStore.recordWorkspaceSubscriptionChange(
                workspaceId,
                operatorOwnerId,
                "workspace_plan_schedule",
                workspaceSlug,
                "Plan scheduled to " + updated.scheduledPlanCode().name(),
                now);
    }

    private void validateRequest(
            WorkspaceSubscriptionStatus subscriptionStatus,
            OffsetDateTime graceUntil,
            WorkspacePlanCode scheduledPlanCode,
            OffsetDateTime scheduledPlanEffectiveAt) {
        if (subscriptionStatus == null) {
            throw new IllegalArgumentException("subscriptionStatus is required");
        }
        if (subscriptionStatus == WorkspaceSubscriptionStatus.GRACE && graceUntil == null) {
            throw new IllegalArgumentException("graceUntil is required when subscriptionStatus is GRACE");
        }
        if (subscriptionStatus != WorkspaceSubscriptionStatus.GRACE && graceUntil != null) {
            throw new IllegalArgumentException("graceUntil is only allowed when subscriptionStatus is GRACE");
        }
        if ((scheduledPlanCode == null) != (scheduledPlanEffectiveAt == null)) {
            throw new IllegalArgumentException(
                    "scheduledPlanCode and scheduledPlanEffectiveAt must be provided together");
        }
    }
}
