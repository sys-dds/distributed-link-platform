package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.OperatorActionLogStore;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceEntitlementService;
import com.linkplatform.api.owner.application.WorkspacePlanCode;
import com.linkplatform.api.owner.application.WorkspacePlanStore;
import com.linkplatform.api.owner.application.WorkspaceRetentionPurgeRunner;
import com.linkplatform.api.owner.application.WorkspaceRetentionPolicyRecord;
import com.linkplatform.api.owner.application.WorkspaceRetentionService;
import com.linkplatform.api.owner.application.WorkspaceStore;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class WorkspacePlanController {

    private final OwnerAccessService ownerAccessService;
    private final WorkspacePlanStore workspacePlanStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final WorkspaceRetentionService workspaceRetentionService;
    private final WorkspaceRetentionPurgeRunner workspaceRetentionPurgeRunner;
    private final WorkspaceStore workspaceStore;
    private final SecurityEventStore securityEventStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final Clock clock;

    public WorkspacePlanController(
            OwnerAccessService ownerAccessService,
            WorkspacePlanStore workspacePlanStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            WorkspaceRetentionService workspaceRetentionService,
            WorkspaceRetentionPurgeRunner workspaceRetentionPurgeRunner,
            WorkspaceStore workspaceStore,
            SecurityEventStore securityEventStore,
            OperatorActionLogStore operatorActionLogStore) {
        this.ownerAccessService = ownerAccessService;
        this.workspacePlanStore = workspacePlanStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspaceRetentionService = workspaceRetentionService;
        this.workspaceRetentionPurgeRunner = workspaceRetentionPurgeRunner;
        this.workspaceStore = workspaceStore;
        this.securityEventStore = securityEventStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/api/v1/workspaces/current/plan")
    public WorkspacePlanResponse currentPlan(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = authorizeMembersRead(apiKey, authorizationHeader, workspaceSlug, request);
        var plan = workspaceEntitlementService.currentPlan(context.workspaceId());
        return new WorkspacePlanResponse(
                context.workspaceSlug(),
                plan.planCode().name(),
                plan.activeLinksLimit(),
                plan.membersLimit(),
                plan.apiKeysLimit(),
                plan.webhooksLimit(),
                plan.monthlyWebhookDeliveriesLimit(),
                plan.exportsEnabled());
    }

    @GetMapping("/api/v1/workspaces/current/usage")
    public WorkspaceUsageSummaryResponse currentUsage(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = authorizeMembersRead(apiKey, authorizationHeader, workspaceSlug, request);
        var usage = workspaceEntitlementService.currentUsage(context.workspaceId());
        return new WorkspaceUsageSummaryResponse(
                context.workspaceSlug(),
                usage.activeLinksCurrent(),
                usage.membersCurrent(),
                usage.apiKeysCurrent(),
                usage.webhooksCurrent(),
                usage.currentMonthWebhookDeliveries(),
                usage.currentMonthWindowStart(),
                usage.currentMonthWindowEnd());
    }

    @GetMapping("/api/v1/workspaces/current/retention")
    public WorkspaceRetentionPolicyResponse currentRetention(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = authorizeRetentionRead(apiKey, authorizationHeader, workspaceSlug, request);
        return toRetentionResponse(workspaceRetentionService.currentPolicy(context.workspaceId()));
    }

    @PatchMapping("/api/v1/workspaces/current/retention")
    public WorkspaceRetentionPolicyResponse updateRetention(
            @RequestBody UpdateWorkspaceRetentionPolicyRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = authorizeRetentionWrite(apiKey, authorizationHeader, workspaceSlug, request);
        WorkspaceRetentionPolicyRecord updated = workspaceRetentionService.updatePolicy(
                context.workspaceId(),
                requestBody.clickHistoryDays(),
                requestBody.securityEventsDays(),
                requestBody.webhookDeliveriesDays(),
                requestBody.abuseCasesDays(),
                requestBody.operatorActionLogDays(),
                context.ownerId());
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "workspace_retention_update",
                null,
                null,
                null,
                "Workspace retention updated",
                OffsetDateTime.now(clock));
        return toRetentionResponse(updated);
    }

    @PatchMapping("/api/v1/ops/workspaces/{workspaceSlug}/plan")
    public WorkspacePlanResponse updatePlan(
            @PathVariable String workspaceSlug,
            @RequestBody UpdateWorkspacePlanRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest request) {
        WorkspaceAccessContext context = authorizeOpsWrite(apiKey, authorizationHeader, workspaceSlug, request);
        var workspace = workspaceStore.findBySlug(workspaceSlug).orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceSlug));
        var plan = workspaceEntitlementService.updatePlan(workspace.id(), WorkspacePlanCode.valueOf(requestBody.planCode()), OffsetDateTime.now(clock));
        securityEventStore.record(
                SecurityEventType.WORKSPACE_PLAN_UPDATED,
                context.ownerId(),
                workspace.id(),
                context.apiKeyHash(),
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                "Workspace plan updated",
                OffsetDateTime.now(clock));
        operatorActionLogStore.record(
                workspace.id(),
                context.ownerId(),
                "PIPELINE",
                "workspace_plan_update",
                null,
                null,
                null,
                "Workspace plan updated to " + plan.planCode().name(),
                OffsetDateTime.now(clock));
        return new WorkspacePlanResponse(
                workspace.slug(),
                plan.planCode().name(),
                plan.activeLinksLimit(),
                plan.membersLimit(),
                plan.apiKeysLimit(),
                plan.webhooksLimit(),
                plan.monthlyWebhookDeliveriesLimit(),
                plan.exportsEnabled());
    }

    @PostMapping("/api/v1/workspaces/current/retention/purge")
    public WorkspaceRetentionPurgeResponse purgeRetention(
            @RequestBody(required = false) RunWorkspaceRetentionPurgeRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.RETENTION_WRITE);
        var result = workspaceRetentionPurgeRunner.runForWorkspace(context);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "workspace_retention_purge",
                null,
                null,
                null,
                "Workspace retention purge executed",
                OffsetDateTime.now(clock));
        return new WorkspaceRetentionPurgeResponse(
                result.webhookDeliveriesDeleted(),
                result.securityEventsDeleted(),
                result.abuseCasesDeleted(),
                result.operatorActionsDeleted(),
                result.clickHistoryDeleted());
    }

    private WorkspaceRetentionPolicyResponse toRetentionResponse(WorkspaceRetentionPolicyRecord record) {
        return new WorkspaceRetentionPolicyResponse(
                record.clickHistoryDays(),
                record.securityEventsDays(),
                record.webhookDeliveriesDays(),
                record.abuseCasesDays(),
                record.operatorActionLogDays(),
                record.updatedAt(),
                record.updatedByOwnerId());
    }

    private WorkspaceAccessContext authorizeMembersRead(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest request) {
        return ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                ApiKeyScope.MEMBERS_READ);
    }

    private WorkspaceAccessContext authorizeRetentionRead(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest request) {
        return ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                ApiKeyScope.RETENTION_READ);
    }

    private WorkspaceAccessContext authorizeRetentionWrite(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest request) {
        return ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                ApiKeyScope.RETENTION_WRITE);
    }

    private WorkspaceAccessContext authorizeOpsWrite(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest request) {
        return ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
    }
}
