package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.AnalyticsOutboxRecord;
import com.linkplatform.api.link.application.AnalyticsOutboxRelay;
import com.linkplatform.api.link.application.AnalyticsOutboxStore;
import com.linkplatform.api.link.application.PipelineControl;
import com.linkplatform.api.link.application.PipelineControlStore;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.JdbcOperatorActionLogStore;
import com.linkplatform.api.owner.application.OperatorActionLogStore;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceEnterprisePolicyService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics/pipeline")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class AnalyticsPipelineController {
    private static final String PIPELINE_NAME = "analytics";

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_REQUEUE_LIMIT = 100;
    private static final int MAX_REQUEUE_LIMIT = 500;
    private static final int MAX_BATCH_IDS = 200;
    private static final int MAX_LOG_DETAIL_LENGTH = 120;

    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final PipelineControlStore pipelineControlStore;
    private final AnalyticsOutboxRelay analyticsOutboxRelay;
    private final OwnerAccessService ownerAccessService;
    private final SecurityEventStore securityEventStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final WorkspaceEnterprisePolicyService workspaceEnterprisePolicyService;
    private final Clock clock;

    public AnalyticsPipelineController(
            AnalyticsOutboxStore analyticsOutboxStore,
            PipelineControlStore pipelineControlStore,
            AnalyticsOutboxRelay analyticsOutboxRelay,
            OwnerAccessService ownerAccessService,
            SecurityEventStore securityEventStore,
            OperatorActionLogStore operatorActionLogStore,
            WorkspaceEnterprisePolicyService workspaceEnterprisePolicyService,
            Clock clock) {
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.pipelineControlStore = pipelineControlStore;
        this.analyticsOutboxRelay = analyticsOutboxRelay;
        this.ownerAccessService = ownerAccessService;
        this.securityEventStore = securityEventStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.workspaceEnterprisePolicyService = workspaceEnterprisePolicyService;
        this.clock = clock;
    }

    @GetMapping
    public AnalyticsPipelineStatusResponse getStatus(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        return buildStatusResponse();
    }

    @GetMapping("/parked")
    public List<AnalyticsPipelineParkedRecordResponse> getParked(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        validateLimit(limit);
        return analyticsOutboxStore.findParked(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/parked/{id}/requeue")
    public ResponseEntity<Void> requeueParked(
            @PathVariable long id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(context, "analytics_pipeline_requeue", httpServletRequest);
        if (!analyticsOutboxStore.requeueParked(id, OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parked analytics outbox row not found: " + id);
        }
        pipelineControlStore.recordRequeue(PIPELINE_NAME, OffsetDateTime.now(clock));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parked/requeue-batch")
    public ResponseEntity<Void> requeueParkedBatch(
            @org.springframework.web.bind.annotation.RequestBody RequeueParkedBatchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(context, "analytics_pipeline_requeue_batch", httpServletRequest);
        List<Long> ids = validateBatchRequest(request);
        analyticsOutboxStore.requeueParkedBatch(ids, OffsetDateTime.now(clock));
        pipelineControlStore.recordRequeue(PIPELINE_NAME, OffsetDateTime.now(clock));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pause")
    public AnalyticsPipelineStatusResponse pause(
            @org.springframework.web.bind.annotation.RequestBody(required = false) UpdatePipelineControlRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(owner, "analytics_pipeline_pause", httpServletRequest);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String reason = request == null ? null : request.reason();
        httpServletRequest.setAttribute("operatorOperation", "analytics_pipeline_pause");
        httpServletRequest.setAttribute("operatorDetail", truncate(reason));
        pipelineControlStore.pause(PIPELINE_NAME, reason, now);
        operatorActionLogStore.record(
                owner.workspaceId(),
                owner.ownerId(),
                "PIPELINE",
                "analytics_pipeline_pause",
                null,
                null,
                null,
                JdbcOperatorActionLogStore.sanitizeNote(reason),
                now);
        securityEventStore.record(
                SecurityEventType.ANALYTICS_PIPELINE_PAUSED,
                owner.ownerId(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                truncate(reason == null ? "Analytics pipeline paused" : "Analytics pipeline paused: " + reason),
                now);
        return buildStatusResponse();
    }

    @PostMapping("/resume")
    public AnalyticsPipelineStatusResponse resume(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(owner, "analytics_pipeline_resume", httpServletRequest);
        OffsetDateTime now = OffsetDateTime.now(clock);
        httpServletRequest.setAttribute("operatorOperation", "analytics_pipeline_resume");
        pipelineControlStore.resume(PIPELINE_NAME, now);
        operatorActionLogStore.record(
                owner.workspaceId(),
                owner.ownerId(),
                "PIPELINE",
                "analytics_pipeline_resume",
                null,
                null,
                null,
                null,
                now);
        securityEventStore.record(
                SecurityEventType.ANALYTICS_PIPELINE_RESUMED,
                owner.ownerId(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Analytics pipeline resumed",
                now);
        return buildStatusResponse();
    }

    @PostMapping("/force-tick")
    public PipelineTickResponse forceTick(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(owner, "analytics_pipeline_force_tick", httpServletRequest);
        OffsetDateTime now = OffsetDateTime.now(clock);
        httpServletRequest.setAttribute("operatorOperation", "analytics_pipeline_force_tick");
        pipelineControlStore.recordForceTick(PIPELINE_NAME, now);
        operatorActionLogStore.record(
                owner.workspaceId(),
                owner.ownerId(),
                "PIPELINE",
                "analytics_pipeline_force_tick",
                null,
                null,
                null,
                null,
                now);
        securityEventStore.record(
                SecurityEventType.ANALYTICS_PIPELINE_FORCE_TICKED,
                owner.ownerId(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Analytics pipeline force ticked",
                now);
        AnalyticsOutboxRelay.RelayIterationResult result = analyticsOutboxRelay.relayOnce();
        return new PipelineTickResponse(
                result.pipelineName(),
                result.paused(),
                result.processedCount(),
                result.parkedCount(),
                analyticsOutboxStore.countEligible(),
                analyticsOutboxStore.countParked());
    }

    @PostMapping("/parked/drain")
    public DrainParkedQueueResponse drainParked(
            @RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        requireOpsApproval(owner, "analytics_pipeline_drain", httpServletRequest);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int requestedLimit = limit == null ? DEFAULT_REQUEUE_LIMIT : limit;
        int appliedLimit = resolveRequeueLimit(limit);
        httpServletRequest.setAttribute("operatorOperation", "analytics_pipeline_drain");
        httpServletRequest.setAttribute("operatorDetail", "limit=" + appliedLimit);
        int movedCount = analyticsOutboxStore.requeueAllParked(appliedLimit);
        PipelineControl control = movedCount > 0
                ? pipelineControlStore.recordRequeue(PIPELINE_NAME, now)
                : pipelineControlStore.get(PIPELINE_NAME);
        operatorActionLogStore.record(
                owner.workspaceId(),
                owner.ownerId(),
                "PIPELINE",
                "analytics_pipeline_parked_drain",
                null,
                null,
                null,
                "limit=" + appliedLimit,
                now);
        securityEventStore.record(
                SecurityEventType.ANALYTICS_PIPELINE_DRAINED,
                owner.ownerId(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Analytics pipeline drained " + movedCount + " parked rows",
                now);
        return new DrainParkedQueueResponse(
                PIPELINE_NAME,
                requestedLimit,
                appliedLimit,
                movedCount,
                analyticsOutboxStore.countParked(),
                control.lastRequeueAt());
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private int resolveRequeueLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_REQUEUE_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return Math.min(limit, MAX_REQUEUE_LIMIT);
    }

    private List<Long> validateBatchRequest(RequeueParkedBatchRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("ids must contain at least one parked row id");
        }
        if (request.ids().size() > MAX_BATCH_IDS) {
            throw new IllegalArgumentException("ids must contain at most " + MAX_BATCH_IDS + " parked row ids");
        }
        if (request.ids().stream().distinct().count() != request.ids().size()) {
            throw new IllegalArgumentException("ids must be unique");
        }
        return request.ids();
    }

    private AnalyticsPipelineStatusResponse buildStatusResponse() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PipelineControl control = pipelineControlStore.get(PIPELINE_NAME);
        return new AnalyticsPipelineStatusResponse(
                control.pipelineName(),
                control.paused(),
                control.pauseReason(),
                analyticsOutboxStore.countEligible(),
                analyticsOutboxStore.countParked(),
                ageSeconds(analyticsOutboxStore.findOldestEligibleAt(), now),
                ageSeconds(analyticsOutboxStore.findOldestParkedAt(), now),
                control.lastRequeueAt(),
                control.lastForceTickAt(),
                control.lastRelaySuccessAt(),
                control.lastRelayFailureAt(),
                control.lastRelayFailureReason());
    }

    private void requireOpsApproval(
            WorkspaceAccessContext context,
            String actionType,
            HttpServletRequest httpServletRequest) {
        workspaceEnterprisePolicyService.requirePrivilegedActionApproval(
                context,
                actionType,
                false,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
    }

    private Double ageSeconds(OffsetDateTime timestamp, OffsetDateTime now) {
        if (timestamp == null) {
            return null;
        }
        return (double) Duration.between(timestamp, now).toSeconds();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LOG_DETAIL_LENGTH ? trimmed : trimmed.substring(0, MAX_LOG_DETAIL_LENGTH);
    }

    private AnalyticsPipelineParkedRecordResponse toResponse(AnalyticsOutboxRecord record) {
        return new AnalyticsPipelineParkedRecordResponse(
                record.id(),
                record.eventId(),
                record.eventKey(),
                record.createdAt(),
                record.attemptCount(),
                record.lastErrorSummary(),
                record.parkedAt());
    }
}
