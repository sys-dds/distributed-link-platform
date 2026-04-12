package com.linkplatform.api.owner.application;

import com.linkplatform.api.owner.api.UpdateWorkspaceEnterprisePolicyRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceEnterprisePolicyService {

    private static final Duration APPROVAL_TTL = Duration.ofMinutes(30);

    private final WorkspaceEnterprisePolicyStore workspaceEnterprisePolicyStore;
    private final PrivilegedActionApprovalStore privilegedActionApprovalStore;
    private final WorkspacePermissionService workspacePermissionService;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public WorkspaceEnterprisePolicyService(
            WorkspaceEnterprisePolicyStore workspaceEnterprisePolicyStore,
            PrivilegedActionApprovalStore privilegedActionApprovalStore,
            WorkspacePermissionService workspacePermissionService,
            SecurityEventStore securityEventStore,
            Clock clock) {
        this.workspaceEnterprisePolicyStore = workspaceEnterprisePolicyStore;
        this.privilegedActionApprovalStore = privilegedActionApprovalStore;
        this.workspacePermissionService = workspacePermissionService;
        this.securityEventStore = securityEventStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkspaceEnterprisePolicyRecord currentPolicy(WorkspaceAccessContext context) {
        workspacePermissionService.requireMembersRead(context);
        return currentPolicyForWorkspace(context);
    }

    @Transactional
    public WorkspaceEnterprisePolicyRecord updatePolicy(
            WorkspaceAccessContext context,
            UpdateWorkspaceEnterprisePolicyRequest request,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        workspacePermissionService.requireScope(context, ApiKeyScope.MEMBERS_WRITE);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceEnterprisePolicyRecord current = currentPolicyForWorkspace(context);
        WorkspaceEnterprisePolicyRecord updated = workspaceEnterprisePolicyStore.update(
                context.workspaceId(),
                request == null || request.requireApiKeyExpiry() == null
                        ? current.requireApiKeyExpiry()
                        : request.requireApiKeyExpiry(),
                normalizeTtl(request == null ? null : request.maxApiKeyTtlDays(), current.maxApiKeyTtlDays()),
                request == null || request.requireServiceAccountKeyExpiry() == null
                        ? current.requireServiceAccountKeyExpiry()
                        : request.requireServiceAccountKeyExpiry(),
                normalizeTtl(
                        request == null ? null : request.maxServiceAccountKeyTtlDays(),
                        current.maxServiceAccountKeyTtlDays()),
                request == null || request.requireDualControlForOps() == null
                        ? current.requireDualControlForOps()
                        : request.requireDualControlForOps(),
                request == null || request.requireDualControlForPlanChanges() == null
                        ? current.requireDualControlForPlanChanges()
                        : request.requireDualControlForPlanChanges(),
                now,
                context.ownerId());
        securityEventStore.record(
                SecurityEventType.WORKSPACE_ENTERPRISE_POLICY_UPDATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                requestMethod,
                requestPath,
                remoteAddress,
                "Workspace enterprise policy updated",
                now);
        return updated;
    }

    @Transactional(readOnly = true)
    public void enforceApiKeyExpiryPolicy(
            WorkspaceAccessContext context,
            OffsetDateTime expiresAt,
            boolean serviceAccountKey,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        WorkspaceEnterprisePolicyRecord policy = currentPolicyForWorkspace(context);
        boolean required = serviceAccountKey
                ? policy.requireServiceAccountKeyExpiry()
                : policy.requireApiKeyExpiry();
        Integer maxTtlDays = serviceAccountKey
                ? policy.maxServiceAccountKeyTtlDays()
                : policy.maxApiKeyTtlDays();
        if (!required && maxTtlDays == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean violation = expiresAt == null
                || expiresAt.isBefore(now)
                || (maxTtlDays != null && expiresAt.isAfter(now.plusDays(maxTtlDays)));
        if (!violation) {
            return;
        }
        securityEventStore.record(
                SecurityEventType.API_KEY_EXPIRY_POLICY_VIOLATION,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                requestMethod,
                requestPath,
                remoteAddress,
                serviceAccountKey ? "Service-account API key expiry policy violated" : "API key expiry policy violated",
                now);
        throw new WorkspaceAccessDeniedException("API key expiry does not satisfy workspace enterprise policy");
    }

    @Transactional
    public void requirePrivilegedActionApproval(
            WorkspaceAccessContext context,
            String actionType,
            boolean planChange,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        WorkspaceEnterprisePolicyRecord policy = currentPolicyForWorkspace(context);
        boolean required = planChange
                ? policy.requireDualControlForPlanChanges()
                : policy.requireDualControlForOps();
        if (!required) {
            return;
        }
        requireHumanOwnerContext(context, "Privileged actions require a HUMAN owner");
        String normalizedActionType = normalizeActionType(actionType);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (privilegedActionApprovalStore.consumeApproved(
                context.workspaceId(),
                normalizedActionType,
                context.ownerId(),
                now).isPresent()) {
            return;
        }
        securityEventStore.record(
                SecurityEventType.PRIVILEGED_ACTION_APPROVAL_REQUESTED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                requestMethod,
                requestPath,
                remoteAddress,
                "Privileged action approval requested: " + normalizedActionType,
                now);
        throw new WorkspaceAccessDeniedException("Privileged action requires approval");
    }

    @Transactional
    public void approvePrivilegedAction(
            WorkspaceAccessContext approver,
            String actionType,
            long initiatorOwnerId,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        workspacePermissionService.requireScope(approver, ApiKeyScope.MEMBERS_WRITE);
        requireHumanOwnerContext(approver, "Privileged action approval requires a HUMAN owner");
        if (approver.ownerId() == initiatorOwnerId) {
            throw new WorkspaceAccessDeniedException("Privileged action approval requires a different HUMAN owner");
        }
        String normalizedActionType = normalizeActionType(actionType);
        OffsetDateTime now = OffsetDateTime.now(clock);
        privilegedActionApprovalStore.createApproved(
                approver.workspaceId(),
                normalizedActionType,
                initiatorOwnerId,
                approver.ownerId(),
                now,
                now.plus(APPROVAL_TTL));
        securityEventStore.record(
                SecurityEventType.PRIVILEGED_ACTION_APPROVED,
                approver.ownerId(),
                approver.workspaceId(),
                approver.apiKeyHash(),
                requestMethod,
                requestPath,
                remoteAddress,
                "Privileged action approved: " + normalizedActionType,
                now);
    }

    public void requireHumanOwnerId(long ownerId, String ownerKey, String message) {
        workspacePermissionService.requireHumanOwnerKey(ownerKey, message);
        if (ownerId <= 0) {
            throw new WorkspaceAccessDeniedException(message);
        }
    }

    private WorkspaceEnterprisePolicyRecord currentPolicyForWorkspace(WorkspaceAccessContext context) {
        return workspaceEnterprisePolicyStore.findOrCreateDefault(
                context.workspaceId(),
                context.ownerId(),
                OffsetDateTime.now(clock));
    }

    private void requireHumanOwnerContext(WorkspaceAccessContext context, String message) {
        requireHumanOwnerId(context.ownerId(), context.ownerKey(), message);
    }

    private Integer normalizeTtl(Integer requested, Integer current) {
        Integer value = requested == null ? current : requested;
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("TTL days must be greater than zero");
        }
        return value;
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType is required");
        }
        return actionType.trim().toUpperCase(Locale.ROOT);
    }
}
