package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OwnerAccessService {

    private final OwnerStore ownerStore;
    private final WorkspaceStore workspaceStore;
    private final WorkspacePermissionService workspacePermissionService;
    private final ApiKeyLifecycleService apiKeyLifecycleService;
    private final ControlPlaneRateLimitStore controlPlaneRateLimitStore;
    private final SecurityEventStore securityEventStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final Clock clock;

    @Autowired
    public OwnerAccessService(
            OwnerStore ownerStore,
            WorkspaceStore workspaceStore,
            WorkspacePermissionService workspacePermissionService,
            ApiKeyLifecycleService apiKeyLifecycleService,
            ControlPlaneRateLimitStore controlPlaneRateLimitStore,
            SecurityEventStore securityEventStore,
            WorkspaceEntitlementService workspaceEntitlementService) {
        this(
                ownerStore,
                workspaceStore,
                workspacePermissionService,
                apiKeyLifecycleService,
                controlPlaneRateLimitStore,
                securityEventStore,
                workspaceEntitlementService,
                Clock.systemUTC());
    }

    public OwnerAccessService(
            OwnerStore ownerStore,
            WorkspaceStore workspaceStore,
            WorkspacePermissionService workspacePermissionService,
            ApiKeyLifecycleService apiKeyLifecycleService,
            ControlPlaneRateLimitStore controlPlaneRateLimitStore,
            SecurityEventStore securityEventStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            Clock clock) {
        this.ownerStore = ownerStore;
        this.workspaceStore = workspaceStore;
        this.workspacePermissionService = workspacePermissionService;
        this.apiKeyLifecycleService = apiKeyLifecycleService;
        this.controlPlaneRateLimitStore = controlPlaneRateLimitStore;
        this.securityEventStore = securityEventStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.clock = clock;
    }

    public WorkspaceAccessContext authorizeRead(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            ApiKeyScope requiredScope) {
        return authorize(apiKey, authorizationHeader, workspaceSlug, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress, Set.of(requiredScope));
    }

    public WorkspaceAccessContext authorizeReadAny(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            Set<ApiKeyScope> acceptedScopes) {
        return authorize(apiKey, authorizationHeader, workspaceSlug, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress, acceptedScopes);
    }

    public WorkspaceAccessContext authorizeMutation(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            ApiKeyScope requiredScope) {
        return authorize(apiKey, authorizationHeader, workspaceSlug, ControlPlaneRateLimitBucket.MUTATION, requestMethod, requestPath, remoteAddress, Set.of(requiredScope));
    }

    public WorkspaceAccessContext authorizeAuthenticated(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        return authorize(apiKey, authorizationHeader, workspaceSlug, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress, Set.of());
    }

    private WorkspaceAccessContext authorize(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            ControlPlaneRateLimitBucket bucket,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            Set<ApiKeyScope> acceptedScopes) {
        String resolvedApiKey = resolveApiKey(apiKey, authorizationHeader, requestMethod, requestPath, remoteAddress);
        if (resolvedApiKey == null) {
            securityEventStore.record(
                    SecurityEventType.MISSING_CREDENTIAL,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Missing API credential rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException(
                    "API credential is required via X-API-Key or Authorization: Bearer <token>");
        }

        String apiKeyHash = sha256(resolvedApiKey);
        OwnerApiKeyRecord apiKeyRecord = apiKeyLifecycleService.authenticate(resolvedApiKey);
        if (apiKeyRecord == null) {
            securityEventStore.record(
                    SecurityEventType.INVALID_CREDENTIAL,
                    null,
                    null,
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Invalid API credential rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("API credential is invalid");
        }
        AuthenticatedOwner owner = ownerStore.findById(apiKeyRecord.ownerId())
                .orElseThrow(() -> new ApiKeyAuthenticationException("API credential is invalid"));

        OffsetDateTime windowStartedAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        int limit = bucket == ControlPlaneRateLimitBucket.READ
                ? owner.plan().readRequestsPerMinute()
                : owner.plan().mutationRequestsPerMinute();
        if (!controlPlaneRateLimitStore.tryConsume(owner.id(), bucket, windowStartedAt, limit)) {
            securityEventStore.record(
                    SecurityEventType.RATE_LIMIT_REJECTED,
                    owner.id(),
                    null,
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Rate limit bucket " + bucket.name() + " exceeded",
                    OffsetDateTime.now(clock));
            throw new ControlPlaneRateLimitExceededException(
                    "Control-plane " + bucket.name().toLowerCase() + " rate limit exceeded");
        }
        WorkspaceRecord workspace = resolveWorkspace(owner.id(), trimToNull(workspaceSlug));
        if (apiKeyRecord.workspaceId() != workspace.id()) {
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_ACCESS_DENIED,
                    owner.id(),
                    workspace.id(),
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Workspace selection denied for API key",
                    OffsetDateTime.now(clock));
            throw new WorkspaceAccessDeniedException("Selected workspace is not available to this API key");
        }
        if (bucket == ControlPlaneRateLimitBucket.MUTATION
                && workspaceStore.isWorkspaceSuspended(workspace.id())
                && !requestPath.endsWith("/status")) {
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_ACCESS_DENIED,
                    owner.id(),
                    workspace.id(),
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Workspace suspended",
                    OffsetDateTime.now(clock));
            throw new WorkspaceAccessDeniedException("Workspace is suspended");
        }
        if (bucket == ControlPlaneRateLimitBucket.MUTATION
                && workspaceEntitlementService.controlPlaneMutationsBlocked(workspace.id())
                && !requestPath.endsWith("/subscription")) {
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_ACCESS_DENIED,
                    owner.id(),
                    workspace.id(),
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Workspace subscription suspended",
                    OffsetDateTime.now(clock));
            throw new WorkspaceAccessDeniedException("Workspace subscription is suspended");
        }
        WorkspaceMemberRecord membership = workspaceStore.findActiveMembership(workspace.id(), owner.id())
                .orElseThrow(() -> {
                    WorkspaceMemberRecord inactiveMembership = workspaceStore.findMembership(workspace.id(), owner.id()).orElse(null);
                    String detail = inactiveMembership != null && inactiveMembership.suspendedAt() != null
                            ? "Workspace member suspended"
                            : "Workspace membership denied";
                    securityEventStore.record(
                            SecurityEventType.WORKSPACE_ACCESS_DENIED,
                            owner.id(),
                            workspace.id(),
                            apiKeyHash,
                            requestMethod,
                            requestPath,
                            remoteAddress,
                            detail,
                            OffsetDateTime.now(clock));
                    if (inactiveMembership != null && inactiveMembership.suspendedAt() != null) {
                        return new WorkspaceAccessDeniedException("Workspace member is suspended");
                    }
                    return new WorkspaceAccessDeniedException("Caller is not an active member of workspace " + workspace.slug());
                });
        WorkspaceAccessContext context = new WorkspaceAccessContext(
                owner,
                workspace.id(),
                workspace.slug(),
                workspace.displayName(),
                workspace.personalWorkspace(),
                membership.role(),
                workspacePermissionService.grantedScopes(membership.role(), apiKeyRecord.scopes()),
                apiKeyHash);
        if (!acceptedScopes.isEmpty()) {
            boolean allowed = acceptedScopes.stream().anyMatch(context.grantedScopes()::contains);
            if (!allowed) {
                SecurityEventType denialType = membership.role().impliedScopes().stream().anyMatch(acceptedScopes::contains)
                        ? SecurityEventType.API_KEY_SCOPE_DENIED
                        : SecurityEventType.WORKSPACE_SCOPE_DENIED;
                securityEventStore.record(
                        denialType,
                        owner.id(),
                        workspace.id(),
                        apiKeyHash,
                        requestMethod,
                        requestPath,
                        remoteAddress,
                        "Workspace scope denied",
                        OffsetDateTime.now(clock));
                throw new WorkspaceScopeDeniedException("Scope denied for requested workspace action");
            }
        }
        apiKeyLifecycleService.markUsed(apiKeyRecord);
        return context;
    }

    private WorkspaceRecord resolveWorkspace(long ownerId, String selectedWorkspaceSlug) {
        if (selectedWorkspaceSlug == null) {
            return workspaceStore.findPersonalWorkspaceByOwnerId(ownerId)
                    .orElseThrow(() -> new WorkspaceNotFoundException("Personal workspace not found for owner " + ownerId));
        }
        return workspaceStore.findBySlug(selectedWorkspaceSlug)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found: " + selectedWorkspaceSlug));
    }

    private String resolveApiKey(
            String apiKeyHeader,
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        String normalizedApiKey = trimToNull(apiKeyHeader);
        String bearerToken = extractBearerToken(authorizationHeader, requestMethod, requestPath, remoteAddress);
        if (normalizedApiKey != null && bearerToken != null && !normalizedApiKey.equals(bearerToken)) {
            securityEventStore.record(
                    SecurityEventType.AMBIGUOUS_CREDENTIAL,
                    null,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Conflicting API credentials rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException(
                    "X-API-Key and Authorization credentials must match when both are provided");
        }
        return normalizedApiKey != null ? normalizedApiKey : bearerToken;
    }

    private String extractBearerToken(
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        String normalizedAuthorization = trimToNull(authorizationHeader);
        if (normalizedAuthorization == null) {
            return null;
        }
        if (!normalizedAuthorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            securityEventStore.record(
                    SecurityEventType.MALFORMED_BEARER,
                    null,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Malformed Authorization header rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("Authorization header must use Bearer token");
        }
        String bearerToken = trimToNull(normalizedAuthorization.substring(7));
        if (bearerToken == null) {
            securityEventStore.record(
                    SecurityEventType.MALFORMED_BEARER,
                    null,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Blank bearer token rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("Authorization bearer token is required");
        }
        return bearerToken;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
