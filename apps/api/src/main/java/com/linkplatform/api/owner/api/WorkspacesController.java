package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.DuplicateWorkspaceMemberException;
import com.linkplatform.api.owner.application.InvalidWorkspaceRoleChangeException;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.OwnerStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceMemberRecord;
import com.linkplatform.api.owner.application.WorkspaceRecord;
import com.linkplatform.api.owner.application.WorkspaceRole;
import com.linkplatform.api.owner.application.WorkspaceStore;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class WorkspacesController {

    private static final Pattern WORKSPACE_SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{3,60}$");

    private final OwnerAccessService ownerAccessService;
    private final OwnerStore ownerStore;
    private final WorkspaceStore workspaceStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public WorkspacesController(
            OwnerAccessService ownerAccessService,
            OwnerStore ownerStore,
            WorkspaceStore workspaceStore,
            SecurityEventStore securityEventStore) {
        this.ownerAccessService = ownerAccessService;
        this.ownerStore = ownerStore;
        this.workspaceStore = workspaceStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    public List<WorkspaceResponse> list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeAuthenticated(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return workspaceStore.findActiveWorkspacesForOwner(context.ownerId()).stream()
                .map(this::toWorkspaceResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @RequestBody CreateWorkspaceRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        validateWorkspaceSlug(request == null ? null : request.slug());
        if (request == null || request.displayName() == null || request.displayName().isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.MEMBERS_WRITE);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceRecord workspace = workspaceStore.createWorkspace(
                request.slug().trim().toLowerCase(Locale.ROOT),
                request.displayName().trim(),
                false,
                now,
                context.ownerId());
        workspaceStore.addMember(workspace.id(), context.ownerId(), WorkspaceRole.OWNER, now, null);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_CREATED,
                context.ownerId(),
                workspace.id(),
                context.apiKeyHash(),
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Workspace created",
                now);
        return new WorkspaceResponse(workspace.id(), workspace.slug(), workspace.displayName(), workspace.personalWorkspace(), WorkspaceRole.OWNER.name().toLowerCase(Locale.ROOT));
    }

    @GetMapping("/current")
    public CurrentWorkspaceResponse current(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeAuthenticated(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return new CurrentWorkspaceResponse(
                context.workspaceId(),
                context.workspaceSlug(),
                context.workspaceDisplayName(),
                context.personalWorkspace(),
                context.role().name().toLowerCase(Locale.ROOT),
                context.grantedScopes().stream().map(scope -> scope.value()).sorted().toList());
    }

    @GetMapping("/{workspaceSlug}/members")
    public List<WorkspaceMemberResponse> listMembers(
            @PathVariable String workspaceSlug,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.MEMBERS_READ);
        return workspaceStore.findActiveMembers(context.workspaceId()).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @PostMapping("/{workspaceSlug}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse addMember(
            @PathVariable String workspaceSlug,
            @RequestBody AddWorkspaceMemberRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.MEMBERS_WRITE);
        ensureSharedWorkspace(context);
        WorkspaceRole role = parseRole(request == null ? null : request.role());
        long ownerId = request == null ? 0L : request.ownerId();
        ownerStore.findById(ownerId).orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
        if (workspaceStore.findActiveMembership(context.workspaceId(), ownerId).isPresent()) {
            throw new DuplicateWorkspaceMemberException("Workspace membership already exists for owner " + ownerId);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        workspaceStore.addMember(context.workspaceId(), ownerId, role, now, context.ownerId());
        securityEventStore.record(
                SecurityEventType.WORKSPACE_MEMBER_ADDED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Workspace member added",
                now);
        return toMemberResponse(workspaceStore.findActiveMembership(context.workspaceId(), ownerId).orElseThrow());
    }

    @PatchMapping("/{workspaceSlug}/members/{ownerId}")
    public WorkspaceMemberResponse updateMember(
            @PathVariable String workspaceSlug,
            @PathVariable long ownerId,
            @RequestBody UpdateWorkspaceMemberRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.MEMBERS_WRITE);
        WorkspaceRole nextRole = parseRole(request == null ? null : request.role());
        WorkspaceMemberRecord existing = workspaceStore.findActiveMembership(context.workspaceId(), ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found for owner " + ownerId));
        if (existing.role() == WorkspaceRole.OWNER && nextRole != WorkspaceRole.OWNER && workspaceStore.countActiveOwners(context.workspaceId()) <= 1) {
            throw new InvalidWorkspaceRoleChangeException("Cannot demote the last OWNER");
        }
        workspaceStore.updateMemberRole(context.workspaceId(), ownerId, nextRole);
        OffsetDateTime now = OffsetDateTime.now(clock);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_MEMBER_ROLE_CHANGED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Workspace member role changed",
                now);
        return toMemberResponse(workspaceStore.findActiveMembership(context.workspaceId(), ownerId).orElseThrow());
    }

    @DeleteMapping("/{workspaceSlug}/members/{ownerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable String workspaceSlug,
            @PathVariable long ownerId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.MEMBERS_WRITE);
        WorkspaceMemberRecord existing = workspaceStore.findActiveMembership(context.workspaceId(), ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found for owner " + ownerId));
        if (existing.role() == WorkspaceRole.OWNER && workspaceStore.countActiveOwners(context.workspaceId()) <= 1) {
            throw new InvalidWorkspaceRoleChangeException("Cannot remove the last OWNER");
        }
        workspaceStore.removeMember(context.workspaceId(), ownerId, OffsetDateTime.now(clock));
        securityEventStore.record(
                SecurityEventType.WORKSPACE_MEMBER_REMOVED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Workspace member removed",
                OffsetDateTime.now(clock));
    }

    private void ensureSharedWorkspace(WorkspaceAccessContext context) {
        if (context.personalWorkspace()) {
            throw new InvalidWorkspaceRoleChangeException("Personal workspaces cannot add members");
        }
    }

    private void validateWorkspaceSlug(String slug) {
        if (slug == null || !WORKSPACE_SLUG_PATTERN.matcher(slug.trim()).matches()) {
            throw new IllegalArgumentException("Workspace slug must match lowercase letters, numbers, hyphen and be 3 to 60 chars");
        }
    }

    private WorkspaceRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        return WorkspaceRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
    }

    private WorkspaceResponse toWorkspaceResponse(WorkspaceRecord record) {
        return new WorkspaceResponse(
                record.id(),
                record.slug(),
                record.displayName(),
                record.personalWorkspace(),
                record.callerRole() == null ? null : record.callerRole().name().toLowerCase(Locale.ROOT));
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMemberRecord record) {
        return new WorkspaceMemberResponse(
                record.ownerId(),
                record.ownerKey(),
                record.displayName(),
                record.role().name().toLowerCase(Locale.ROOT),
                record.joinedAt());
    }
}
