package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceAbusePolicyStore {

    Optional<WorkspaceAbusePolicyRecord> findPolicy(long workspaceId);

    WorkspaceAbusePolicyRecord upsertPolicy(
            long workspaceId,
            boolean rawIpReviewEnabled,
            boolean punycodeReviewEnabled,
            int repeatedHostQuarantineThreshold,
            int redirectRateLimitQuarantineThreshold,
            OffsetDateTime updatedAt,
            long updatedByOwnerId);

    List<WorkspaceHostRuleRecord> findHostRules(long workspaceId);

    Optional<WorkspaceHostRuleRecord> findHostRule(long workspaceId, String host, String ruleType);

    WorkspaceHostRuleRecord createHostRule(
            long workspaceId,
            String host,
            String ruleType,
            String note,
            OffsetDateTime createdAt,
            long createdByOwnerId);

    boolean deleteHostRule(long workspaceId, long ruleId);

    void incrementHostSignal(long workspaceId, String host, OffsetDateTime signaledAt);

    long findHostSignalCount(long workspaceId, String host);

    List<WorkspaceAbuseTrendRecord> findTopFlaggedHosts(long workspaceId, OffsetDateTime since, int limit);

    List<WorkspaceAbuseTrendRecord> findTopQuarantinedHosts(long workspaceId, OffsetDateTime since, int limit);

    Optional<OffsetDateTime> findLatestUpdatedAt(long workspaceId);
}
