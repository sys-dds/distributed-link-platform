package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkAbuseCaseStatus;
import com.linkplatform.api.link.application.LinkAbuseReviewService;
import com.linkplatform.api.link.application.AnalyticsOutboxRelay;
import com.linkplatform.api.link.application.GovernanceRollupStore;
import com.linkplatform.api.link.application.LinkLifecycleOutboxRelay;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.PipelineHealthIndicator;
import com.linkplatform.api.runtime.QueryDataSourceHealthIndicator;
import com.linkplatform.api.runtime.RedirectRuntimeHealthIndicator;
import com.linkplatform.api.runtime.RuntimeMode;
import com.linkplatform.api.runtime.RuntimeRoleHealthIndicator;
import com.linkplatform.api.projection.ProjectionJobStore;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/status")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class OpsStatusController {

    private final OwnerAccessService ownerAccessService;
    private final RuntimeRoleHealthIndicator runtimeRoleHealthIndicator;
    private final RedirectRuntimeHealthIndicator redirectRuntimeHealthIndicator;
    private final QueryDataSourceHealthIndicator queryDataSourceHealthIndicator;
    private final PipelineHealthIndicator pipelineHealthIndicator;
    private final ProjectionJobStore projectionJobStore;
    private final LinkAbuseReviewService linkAbuseReviewService;
    private final GovernanceRollupStore governanceRollupStore;
    private final Clock clock;

    public OpsStatusController(
            OwnerAccessService ownerAccessService,
            RuntimeRoleHealthIndicator runtimeRoleHealthIndicator,
            RedirectRuntimeHealthIndicator redirectRuntimeHealthIndicator,
            QueryDataSourceHealthIndicator queryDataSourceHealthIndicator,
            PipelineHealthIndicator pipelineHealthIndicator,
            ProjectionJobStore projectionJobStore,
            LinkAbuseReviewService linkAbuseReviewService,
            GovernanceRollupStore governanceRollupStore,
            Clock clock) {
        this.ownerAccessService = ownerAccessService;
        this.runtimeRoleHealthIndicator = runtimeRoleHealthIndicator;
        this.redirectRuntimeHealthIndicator = redirectRuntimeHealthIndicator;
        this.queryDataSourceHealthIndicator = queryDataSourceHealthIndicator;
        this.pipelineHealthIndicator = pipelineHealthIndicator;
        this.projectionJobStore = projectionJobStore;
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.governanceRollupStore = governanceRollupStore;
        this.clock = clock;
    }

    @GetMapping
    public OpsStatusResponse getStatus(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        OffsetDateTime now = OffsetDateTime.now(clock);
        var analytics = pipelineHealthIndicator.snapshot(AnalyticsOutboxRelay.PIPELINE_NAME);
        var lifecycle = pipelineHealthIndicator.snapshot(LinkLifecycleOutboxRelay.PIPELINE_NAME);
        var redirect = redirectRuntimeHealthIndicator.snapshot();
        var query = queryDataSourceHealthIndicator.snapshot();
        return new OpsStatusResponse(
                context.workspaceSlug(),
                runtimeRoleHealthIndicator.runtimeMode().name(),
                now,
                redirectRuntimeMap(redirect),
                queryRuntimeMap(query),
                Map.of(
                        "analytics", toPipelineResponse(analytics),
                        "lifecycle", toPipelineResponse(lifecycle)),
                new OpsProjectionSummaryResponse(
                        projectionJobStore.countQueued(context.workspaceId()),
                        projectionJobStore.countActive(context.workspaceId()),
                        projectionJobStore.countFailed(context.workspaceId()),
                        projectionJobStore.countCompleted(context.workspaceId()),
                        projectionJobStore.findLatestStartedAt(context.workspaceId()).orElse(null),
                        projectionJobStore.findLatestFailedAt(context.workspaceId()).orElse(null)),
                new OpsAbuseSummaryResponse(
                        linkAbuseReviewService.countCasesByStatus(context.workspaceId(), LinkAbuseCaseStatus.OPEN),
                        linkAbuseReviewService.countCasesByStatus(context.workspaceId(), LinkAbuseCaseStatus.QUARANTINED),
                        linkAbuseReviewService.countCasesResolvedOnDay(context.workspaceId(), LinkAbuseCaseStatus.RELEASED, LocalDate.now(clock)),
                        linkAbuseReviewService.countCasesResolvedOnDay(context.workspaceId(), LinkAbuseCaseStatus.DISMISSED, LocalDate.now(clock)),
                        linkAbuseReviewService.findLatestUpdatedAt(context.workspaceId())),
                GlobalGovernanceSummaryResponse.from(governanceRollupStore.summary(now)));
    }

    private OpsPipelineSummaryResponse toPipelineResponse(PipelineHealthIndicator.PipelineSnapshot snapshot) {
        return new OpsPipelineSummaryResponse(
                snapshot.paused(),
                snapshot.eligibleCount(),
                snapshot.parkedCount(),
                snapshot.oldestEligibleAgeSeconds(),
                snapshot.oldestParkedAgeSeconds(),
                snapshot.lastRequeueAt(),
                snapshot.lastForceTickAt(),
                snapshot.lastRelaySuccessAt(),
                snapshot.lastRelayFailureAt(),
                snapshot.lastRelayFailureReason());
    }

    private Map<String, Object> redirectRuntimeMap(RedirectRuntimeHealthIndicator.RedirectRuntimeSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", snapshot.enabled());
        map.put("degraded", snapshot.degraded());
        map.put("failoverConfigured", snapshot.failoverConfigured());
        map.put("lastDecision", snapshot.lastDecision());
        return map;
    }

    private Map<String, Object> queryRuntimeMap(QueryDataSourceHealthIndicator.QueryRuntimeSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dedicatedQueryConfigured", snapshot.dedicatedQueryConfigured());
        map.put("replicaEnabled", snapshot.replicaEnabled());
        map.put("lagSeconds", snapshot.lagSeconds());
        map.put("heartbeatAgeSeconds", snapshot.heartbeatAgeSeconds());
        map.put("fallbackActive", snapshot.fallbackActive());
        map.put("lastFallbackAt", snapshot.lastFallbackAt());
        map.put("lastFallbackReason", snapshot.lastFallbackReason());
        return map;
    }
}
