package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceRetentionService {

    private final WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;
    private final Clock clock;

    public WorkspaceRetentionService(WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore, Clock clock) {
        this.workspaceRetentionPolicyStore = workspaceRetentionPolicyStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkspaceRetentionPolicyRecord currentPolicy(long workspaceId) {
        return workspaceRetentionPolicyStore.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace retention policy not found"));
    }

    @Transactional
    public WorkspaceRetentionPolicyRecord updatePolicy(
            long workspaceId,
            int clickHistoryDays,
            int securityEventsDays,
            int webhookDeliveriesDays,
            int abuseCasesDays,
            int operatorActionLogDays,
            long updatedByOwnerId) {
        validateDays(clickHistoryDays);
        validateDays(securityEventsDays);
        validateDays(webhookDeliveriesDays);
        validateDays(abuseCasesDays);
        validateDays(operatorActionLogDays);
        return workspaceRetentionPolicyStore.upsert(
                workspaceId,
                clickHistoryDays,
                securityEventsDays,
                webhookDeliveriesDays,
                abuseCasesDays,
                operatorActionLogDays,
                OffsetDateTime.now(clock),
                updatedByOwnerId);
    }

    private void validateDays(int days) {
        if (days < 7 || days > 3650) {
            throw new IllegalArgumentException("Retention values must be between 7 and 3650 inclusive");
        }
    }
}
