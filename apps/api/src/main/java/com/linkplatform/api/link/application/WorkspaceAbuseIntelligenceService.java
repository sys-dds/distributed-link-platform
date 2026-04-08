package com.linkplatform.api.link.application;

import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspacePermissionService;
import java.net.IDN;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceAbuseIntelligenceService {

    private static final int DEFAULT_REPEATED_HOST_THRESHOLD = 3;
    private static final int DEFAULT_REDIRECT_THRESHOLD = 5;

    private final WorkspaceAbusePolicyStore workspaceAbusePolicyStore;
    private final WorkspacePermissionService workspacePermissionService;
    private final LinkAbuseStore linkAbuseStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public WorkspaceAbuseIntelligenceService(
            WorkspaceAbusePolicyStore workspaceAbusePolicyStore,
            WorkspacePermissionService workspacePermissionService,
            LinkAbuseStore linkAbuseStore,
            SecurityEventStore securityEventStore) {
        this.workspaceAbusePolicyStore = workspaceAbusePolicyStore;
        this.workspacePermissionService = workspacePermissionService;
        this.linkAbuseStore = linkAbuseStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public WorkspaceAbusePolicyRecord currentPolicy(WorkspaceAccessContext context) {
        workspacePermissionService.requireOpsRead(context);
        return policyForWorkspace(context.workspaceId());
    }

    @Transactional
    public WorkspaceAbusePolicyRecord updatePolicy(
            WorkspaceAccessContext context,
            Boolean rawIpReviewEnabled,
            Boolean punycodeReviewEnabled,
            Integer repeatedHostQuarantineThreshold,
            Integer redirectRateLimitQuarantineThreshold) {
        workspacePermissionService.requireOpsWrite(context);
        WorkspaceAbusePolicyRecord current = policyForWorkspace(context.workspaceId());
        int repeatedThreshold = repeatedHostQuarantineThreshold == null
                ? current.repeatedHostQuarantineThreshold()
                : repeatedHostQuarantineThreshold;
        int redirectThreshold = redirectRateLimitQuarantineThreshold == null
                ? current.redirectRateLimitQuarantineThreshold()
                : redirectRateLimitQuarantineThreshold;
        if (repeatedThreshold <= 0 || redirectThreshold <= 0) {
            throw new IllegalArgumentException("Abuse thresholds must be positive");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceAbusePolicyRecord updated = workspaceAbusePolicyStore.upsertPolicy(
                context.workspaceId(),
                rawIpReviewEnabled == null ? current.rawIpReviewEnabled() : rawIpReviewEnabled,
                punycodeReviewEnabled == null ? current.punycodeReviewEnabled() : punycodeReviewEnabled,
                repeatedThreshold,
                redirectThreshold,
                now,
                context.ownerId());
        securityEventStore.record(
                SecurityEventType.WORKSPACE_ABUSE_POLICY_UPDATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "PATCH",
                "/api/v1/workspaces/current/abuse/policy",
                null,
                "Workspace abuse policy updated",
                now);
        return updated;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceHostRuleRecord> listHostRules(WorkspaceAccessContext context) {
        workspacePermissionService.requireOpsRead(context);
        return workspaceAbusePolicyStore.findHostRules(context.workspaceId());
    }

    @Transactional
    public WorkspaceHostRuleRecord createHostRule(WorkspaceAccessContext context, String host, String ruleType, String note) {
        workspacePermissionService.requireOpsWrite(context);
        String normalizedHost = normalizeHost(host);
        String normalizedRuleType = normalizeRuleType(ruleType);
        if (workspaceAbusePolicyStore.findHostRule(context.workspaceId(), normalizedHost, normalizedRuleType).isPresent()) {
            throw new IllegalArgumentException("Workspace host rule already exists");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceHostRuleRecord record = workspaceAbusePolicyStore.createHostRule(
                context.workspaceId(),
                normalizedHost,
                normalizedRuleType,
                sanitizeNote(note),
                now,
                context.ownerId());
        securityEventStore.record(
                SecurityEventType.WORKSPACE_HOST_RULE_CREATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/abuse/host-rules",
                null,
                "Workspace host rule created",
                now);
        return record;
    }

    @Transactional
    public void deleteHostRule(WorkspaceAccessContext context, long ruleId) {
        workspacePermissionService.requireOpsWrite(context);
        if (!workspaceAbusePolicyStore.deleteHostRule(context.workspaceId(), ruleId)) {
            throw new IllegalArgumentException("Workspace host rule not found");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_HOST_RULE_DELETED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "DELETE",
                "/api/v1/workspaces/current/abuse/host-rules/" + ruleId,
                null,
                "Workspace host rule deleted",
                now);
    }

    @Transactional(readOnly = true)
    public AbuseTrendSummary trends(WorkspaceAccessContext context) {
        workspacePermissionService.requireOpsRead(context);
        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(7);
        return new AbuseTrendSummary(
                workspaceAbusePolicyStore.findTopFlaggedHosts(context.workspaceId(), since, 5),
                workspaceAbusePolicyStore.findTopQuarantinedHosts(context.workspaceId(), since, 5),
                linkAbuseStore.countCasesByStatus(context.workspaceId(), LinkAbuseCaseStatus.OPEN),
                linkAbuseStore.countQuarantinedLinks(context.workspaceId()),
                latestUpdatedAt(context.workspaceId()));
    }

    @Transactional(readOnly = true)
    public HostRuleEffect hostRuleEffect(long workspaceId, String normalizedHost) {
        if (normalizedHost == null) {
            return HostRuleEffect.NONE;
        }
        if (workspaceAbusePolicyStore.findHostRule(workspaceId, normalizedHost, "DENY").isPresent()) {
            return HostRuleEffect.DENY;
        }
        if (workspaceAbusePolicyStore.findHostRule(workspaceId, normalizedHost, "ALLOW").isPresent()) {
            return HostRuleEffect.ALLOW;
        }
        return HostRuleEffect.NONE;
    }

    @Transactional(readOnly = true)
    public WorkspaceAbusePolicyRecord policyForWorkspace(long workspaceId) {
        return workspaceAbusePolicyStore.findPolicy(workspaceId).orElse(defaultPolicy(workspaceId));
    }

    @Transactional
    public void recordHostSignal(long workspaceId, String normalizedHost) {
        if (normalizedHost == null) {
            return;
        }
        workspaceAbusePolicyStore.incrementHostSignal(workspaceId, normalizedHost, OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public boolean repeatedHostThresholdReached(long workspaceId, String normalizedHost) {
        if (normalizedHost == null) {
            return false;
        }
        WorkspaceAbusePolicyRecord policy = policyForWorkspace(workspaceId);
        return workspaceAbusePolicyStore.findHostSignalCount(workspaceId, normalizedHost) >= policy.repeatedHostQuarantineThreshold();
    }

    @Transactional(readOnly = true)
    public int redirectRateLimitThreshold(long workspaceId) {
        return policyForWorkspace(workspaceId).redirectRateLimitQuarantineThreshold();
    }

    public String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        String trimmed = host.trim();
        if (trimmed.contains("*")) {
            throw new IllegalArgumentException("Host rules do not support wildcards");
        }
        try {
            return IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Host is invalid");
        }
    }

    private String normalizeRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            throw new IllegalArgumentException("ruleType is required");
        }
        String normalized = ruleType.trim().toUpperCase(Locale.ROOT);
        if (!"ALLOW".equals(normalized) && !"DENY".equals(normalized)) {
            throw new IllegalArgumentException("ruleType must be ALLOW or DENY");
        }
        return normalized;
    }

    private String sanitizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private WorkspaceAbusePolicyRecord defaultPolicy(long workspaceId) {
        return new WorkspaceAbusePolicyRecord(
                workspaceId,
                true,
                true,
                DEFAULT_REPEATED_HOST_THRESHOLD,
                DEFAULT_REDIRECT_THRESHOLD,
                null,
                null);
    }

    private OffsetDateTime latestUpdatedAt(long workspaceId) {
        OffsetDateTime policyLatest = workspaceAbusePolicyStore.findLatestUpdatedAt(workspaceId).orElse(null);
        OffsetDateTime abuseLatest = linkAbuseStore.findLatestUpdatedAt(workspaceId).orElse(null);
        if (policyLatest == null) {
            return abuseLatest;
        }
        if (abuseLatest == null) {
            return policyLatest;
        }
        return policyLatest.isAfter(abuseLatest) ? policyLatest : abuseLatest;
    }

    public enum HostRuleEffect {
        NONE,
        ALLOW,
        DENY
    }

    public record AbuseTrendSummary(
            List<WorkspaceAbuseTrendRecord> topFlaggedHostsLast7d,
            List<WorkspaceAbuseTrendRecord> topQuarantinedHostsLast7d,
            long totalOpenAbuseCases,
            long totalQuarantinedLinks,
            OffsetDateTime latestUpdatedAt) {
    }
}
