package com.linkplatform.api.owner.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceLifecycleService {

    private final WorkspaceStore workspaceStore;
    private final ServiceAccountStore serviceAccountStore;
    private final SecurityEventStore securityEventStore;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public WorkspaceLifecycleService(
            WorkspaceStore workspaceStore,
            ServiceAccountStore serviceAccountStore,
            SecurityEventStore securityEventStore,
            JdbcTemplate jdbcTemplate) {
        this.workspaceStore = workspaceStore;
        this.serviceAccountStore = serviceAccountStore;
        this.securityEventStore = securityEventStore;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public ServiceAccountRecord createServiceAccount(
            WorkspaceAccessContext context,
            String name,
            String slug,
            WorkspaceRole role) {
        requireActiveWorkspace(context.workspaceId());
        String normalizedName = normalizeName(name);
        String normalizedSlug = normalizeServiceAccountSlug(slug);
        serviceAccountStore.findByWorkspaceIdAndSlug(context.workspaceId(), normalizedSlug)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Service account slug already exists");
                });
        long ownerId = nextOwnerId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update(
                "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (?, ?, ?, 'FREE', ?)",
                ownerId,
                "svc-" + context.workspaceId() + "-" + normalizedSlug,
                normalizedName,
                now);
        ServiceAccountRecord record = serviceAccountStore.create(
                ownerId,
                context.workspaceId(),
                normalizedName,
                normalizedSlug,
                ServiceAccountStatus.ACTIVE,
                now,
                context.ownerId());
        workspaceStore.addServiceAccountMember(context.workspaceId(), ownerId, role, now, context.ownerId());
        securityEventStore.record(
                SecurityEventType.SERVICE_ACCOUNT_CREATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/service-accounts",
                null,
                "Service account created",
                now);
        return record;
    }

    @Transactional
    public ServiceAccountRecord disableServiceAccount(WorkspaceAccessContext context, long serviceAccountId) {
        requireActiveWorkspace(context.workspaceId());
        ServiceAccountRecord serviceAccount = serviceAccountStore.findById(serviceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Service account not found"));
        if (serviceAccount.workspaceId() != context.workspaceId()) {
            throw new IllegalArgumentException("Service account not found");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        serviceAccountStore.disable(serviceAccountId, now, context.ownerId());
        workspaceStore.suspendMember(context.workspaceId(), serviceAccountId, now, context.ownerId(), "service account disabled");
        securityEventStore.record(
                SecurityEventType.SERVICE_ACCOUNT_DISABLED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/service-accounts/" + serviceAccountId + "/disable",
                null,
                "Service account disabled",
                now);
        return serviceAccountStore.findById(serviceAccountId).orElseThrow();
    }

    @Transactional
    public WorkspaceMemberRecord suspendMember(WorkspaceAccessContext context, long ownerId, String reason) {
        requireActiveWorkspace(context.workspaceId());
        WorkspaceMemberRecord member = requireMembership(context.workspaceId(), ownerId);
        requireSuspendableMember(context.workspaceId(), member);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!workspaceStore.suspendMember(context.workspaceId(), ownerId, now, context.ownerId(), sanitizeReason(reason))) {
            throw new IllegalArgumentException("Workspace member cannot be suspended");
        }
        recordMemberSuspended(context, ownerId, now);
        return workspaceStore.findMembership(context.workspaceId(), ownerId).orElseThrow();
    }

    @Transactional
    public WorkspaceMemberRecord resumeMember(WorkspaceAccessContext context, long ownerId) {
        requireActiveWorkspace(context.workspaceId());
        requireMembership(context.workspaceId(), ownerId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!workspaceStore.resumeMember(context.workspaceId(), ownerId)) {
            throw new IllegalArgumentException("Workspace member cannot be resumed");
        }
        recordMemberResumed(context, ownerId, now);
        return workspaceStore.findMembership(context.workspaceId(), ownerId).orElseThrow();
    }

    @Transactional
    public void suspendWorkspace(WorkspaceAccessContext context, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!workspaceStore.suspendWorkspace(context.workspaceId(), now, context.ownerId(), sanitizeReason(reason))) {
            throw new IllegalArgumentException("Workspace cannot be suspended");
        }
        recordWorkspaceSuspended(context, now);
    }

    @Transactional
    public void resumeWorkspace(WorkspaceAccessContext context) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!workspaceStore.resumeWorkspace(context.workspaceId())) {
            throw new IllegalArgumentException("Workspace cannot be resumed");
        }
        recordWorkspaceResumed(context, now);
    }

    @Transactional
    public void transferOwnership(WorkspaceAccessContext context, long fromOwnerId, long toOwnerId) {
        requireActiveWorkspace(context.workspaceId());
        WorkspaceMemberRecord from = requireActiveHumanMembership(context.workspaceId(), fromOwnerId);
        WorkspaceMemberRecord to = requireActiveHumanMembership(context.workspaceId(), toOwnerId);
        if (!from.role().ownerLike()) {
            throw new InvalidWorkspaceRoleChangeException("Transfer source must be an active HUMAN OWNER");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        workspaceStore.updateMemberRole(context.workspaceId(), toOwnerId, WorkspaceRole.OWNER);
        workspaceStore.updateMemberRole(context.workspaceId(), fromOwnerId, WorkspaceRole.ADMIN);
        recordOwnershipTransferred(context, now);
    }

    private void requireActiveWorkspace(long workspaceId) {
        if (workspaceStore.isWorkspaceSuspended(workspaceId)) {
            throw new WorkspaceAccessDeniedException("Workspace is suspended");
        }
    }

    private WorkspaceMemberRecord requireMembership(long workspaceId, long ownerId) {
        return workspaceStore.findMembership(workspaceId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found for owner " + ownerId));
    }

    private WorkspaceMemberRecord requireActiveHumanMembership(long workspaceId, long ownerId) {
        WorkspaceMemberRecord member = workspaceStore.findActiveMembership(workspaceId, ownerId)
                .orElseThrow(() -> new InvalidWorkspaceRoleChangeException("Ownership transfer requires active HUMAN members"));
        if (!"HUMAN".equals(member.memberType())) {
            throw new InvalidWorkspaceRoleChangeException("Ownership transfer requires active HUMAN members");
        }
        return member;
    }

    private void requireSuspendableMember(long workspaceId, WorkspaceMemberRecord member) {
        if ("HUMAN".equals(member.memberType())
                && member.role() == WorkspaceRole.OWNER
                && workspaceStore.countActiveHumanOwners(workspaceId) <= 1) {
            throw new InvalidWorkspaceRoleChangeException("Cannot suspend the last active HUMAN OWNER");
        }
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service account name is required");
        }
        String trimmed = name.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private String normalizeServiceAccountSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Service account slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9-]{3,60}$")) {
            throw new IllegalArgumentException("Service account slug must match lowercase letters, numbers, hyphen and be 3 to 60 chars");
        }
        return normalized;
    }

    private long nextOwnerId() {
        Long ownerId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM owners", Long.class);
        if (ownerId == null) {
            throw new IllegalStateException("Unable to allocate service account owner id");
        }
        return ownerId;
    }

    private void recordMemberSuspended(WorkspaceAccessContext context, long ownerId, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_MEMBER_SUSPENDED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/members/" + ownerId + "/suspend",
                null,
                "Workspace member suspended",
                now);
    }

    private void recordMemberResumed(WorkspaceAccessContext context, long ownerId, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_MEMBER_RESUMED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/members/" + ownerId + "/resume",
                null,
                "Workspace member resumed",
                now);
    }

    private void recordWorkspaceSuspended(WorkspaceAccessContext context, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_SUSPENDED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/suspend",
                null,
                "Workspace suspended",
                now);
    }

    private void recordWorkspaceResumed(WorkspaceAccessContext context, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_RESUMED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/resume",
                null,
                "Workspace resumed",
                now);
    }

    private void recordOwnershipTransferred(WorkspaceAccessContext context, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_OWNERSHIP_TRANSFERRED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/ownership-transfer",
                null,
                "Workspace ownership transferred",
                now);
    }
}
