package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface WorkspacePlanStore {

    Optional<WorkspacePlanRecord> findByWorkspaceId(long workspaceId);

    WorkspacePlanRecord upsertPlan(long workspaceId, WorkspacePlanCode planCode, OffsetDateTime updatedAt);

    WorkspacePlanRecord updateSubscriptionLifecycle(
            long workspaceId,
            WorkspaceSubscriptionStatus subscriptionStatus,
            OffsetDateTime graceUntil,
            WorkspacePlanCode scheduledPlanCode,
            OffsetDateTime scheduledPlanEffectiveAt,
            OffsetDateTime updatedAt);

    default long countOverQuotaWorkspaces() {
        throw new UnsupportedOperationException("Global over-quota workspace count is not implemented");
    }
}
