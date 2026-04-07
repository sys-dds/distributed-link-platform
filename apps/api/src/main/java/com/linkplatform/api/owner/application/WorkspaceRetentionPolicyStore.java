package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface WorkspaceRetentionPolicyStore {

    Optional<WorkspaceRetentionPolicyRecord> findByWorkspaceId(long workspaceId);

    WorkspaceRetentionPolicyRecord upsert(
            long workspaceId,
            int clickHistoryDays,
            int securityEventsDays,
            int webhookDeliveriesDays,
            int abuseCasesDays,
            int operatorActionLogDays,
            OffsetDateTime updatedAt,
            long updatedByOwnerId);

    long purgeSecurityEvents(long workspaceId, OffsetDateTime cutoff);

    long purgeOperatorActions(long workspaceId, OffsetDateTime cutoff);
}
