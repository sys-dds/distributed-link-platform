package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkLifecycleOutboxRecord;
import com.linkplatform.api.link.application.LinkLifecycleOutboxRelay;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.PipelineControl;
import com.linkplatform.api.link.application.PipelineControlStore;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
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
@RequestMapping("/api/v1/lifecycle/pipeline")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class LifecyclePipelineController {
    private static final String PIPELINE_NAME = "lifecycle";

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_REQUEUE_LIMIT = 100;
    private static final int MAX_REQUEUE_LIMIT = 500;
    private static final int MAX_BATCH_IDS = 200;
    private static final int MAX_LOG_DETAIL_LENGTH = 120;

    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final PipelineControlStore pipelineControlStore;
    private final LinkLifecycleOutboxRelay linkLifecycleOutboxRelay;
    private final OwnerAccessService ownerAccessService;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public LifecyclePipelineController(
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            PipelineControlStore pipelineControlStore,
            LinkLifecycleOutboxRelay linkLifecycleOutboxRelay,
            OwnerAccessService ownerAccessService,
            SecurityEventStore securityEventStore) {
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.pipelineControlStore = pipelineControlStore;
        this.linkLifecycleOutboxRelay = linkLifecycleOutboxRelay;
        this.ownerAccessService = ownerAccessService;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    public LifecyclePipelineStatusResponse getStatus(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return buildStatusResponse();
    }

    @GetMapping("/parked")
    public List<LifecyclePipelineParkedRecordResponse> getParked(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        validateLimit(limit);
        return linkLifecycleOutboxStore.findParked(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/parked/{id}/requeue")
    public ResponseEntity<Void> requeueParked(
            @PathVariable long id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        if (!linkLifecycleOutboxStore.requeueParked(id, OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parked lifecycle outbox row not found: " + id);
        }
        pipelineControlStore.recordRequeue(PIPELINE_NAME, OffsetDateTime.now(clock));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parked/requeue-batch")
    public ResponseEntity<Void> requeueParkedBatch(
            @org.springframework.web.bind.annotation.RequestBody RequeueParkedBatchRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        List<Long> ids = validateBatchRequest(request);
        linkLifecycleOutboxStore.requeueParkedBatch(ids, OffsetDateTime.now(clock));
        pipelineControlStore.recordRequeue(PIPELINE_NAME, OffsetDateTime.now(clock));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pause")
    public LifecyclePipelineStatusResponse pause(
            @org.springframework.web.bind.annotation.RequestBody(required = false) UpdatePipelineControlRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        OffsetDateTime now = OffsetDateTime.now(clock);
        String reason = request == null ? null : request.reason();
        httpServletRequest.setAttribute("operatorOperation", "lifecycle_pipeline_pause");
        httpServletRequest.setAttribute("operatorDetail", truncate(reason));
        pipelineControlStore.pause(PIPELINE_NAME, reason, now);
        securityEventStore.record(
                SecurityEventType.LIFECYCLE_PIPELINE_PAUSED,
                owner.id(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                truncate(reason == null ? "Lifecycle pipeline paused" : "Lifecycle pipeline paused: " + reason),
                now);
        return buildStatusResponse();
    }

    @PostMapping("/resume")
    public LifecyclePipelineStatusResponse resume(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        OffsetDateTime now = OffsetDateTime.now(clock);
        httpServletRequest.setAttribute("operatorOperation", "lifecycle_pipeline_resume");
        pipelineControlStore.resume(PIPELINE_NAME, now);
        securityEventStore.record(
                SecurityEventType.LIFECYCLE_PIPELINE_RESUMED,
                owner.id(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Lifecycle pipeline resumed",
                now);
        return buildStatusResponse();
    }

    @PostMapping("/force-tick")
    public PipelineTickResponse forceTick(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        OffsetDateTime now = OffsetDateTime.now(clock);
        httpServletRequest.setAttribute("operatorOperation", "lifecycle_pipeline_force_tick");
        pipelineControlStore.recordForceTick(PIPELINE_NAME, now);
        securityEventStore.record(
                SecurityEventType.LIFECYCLE_PIPELINE_FORCE_TICKED,
                owner.id(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Lifecycle pipeline force ticked",
                now);
        LinkLifecycleOutboxRelay.RelayIterationResult result = linkLifecycleOutboxRelay.relayOnce();
        return new PipelineTickResponse(
                result.pipelineName(),
                result.paused(),
                result.processedCount(),
                result.parkedCount(),
                linkLifecycleOutboxStore.countEligible(),
                linkLifecycleOutboxStore.countParked());
    }

    @PostMapping("/parked/drain")
    public DrainParkedQueueResponse drainParked(
            @RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        var owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        OffsetDateTime now = OffsetDateTime.now(clock);
        int requestedLimit = limit == null ? DEFAULT_REQUEUE_LIMIT : limit;
        int appliedLimit = resolveRequeueLimit(limit);
        httpServletRequest.setAttribute("operatorOperation", "lifecycle_pipeline_drain");
        httpServletRequest.setAttribute("operatorDetail", "limit=" + appliedLimit);
        int movedCount = linkLifecycleOutboxStore.requeueAllParked(appliedLimit);
        PipelineControl control = movedCount > 0
                ? pipelineControlStore.recordRequeue(PIPELINE_NAME, now)
                : pipelineControlStore.get(PIPELINE_NAME);
        securityEventStore.record(
                SecurityEventType.LIFECYCLE_PIPELINE_DRAINED,
                owner.id(),
                null,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                "Lifecycle pipeline drained " + movedCount + " parked rows",
                now);
        return new DrainParkedQueueResponse(
                PIPELINE_NAME,
                requestedLimit,
                appliedLimit,
                movedCount,
                linkLifecycleOutboxStore.countParked(),
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

    private LifecyclePipelineStatusResponse buildStatusResponse() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PipelineControl control = pipelineControlStore.get(PIPELINE_NAME);
        return new LifecyclePipelineStatusResponse(
                control.pipelineName(),
                control.paused(),
                control.pauseReason(),
                linkLifecycleOutboxStore.countEligible(),
                linkLifecycleOutboxStore.countParked(),
                ageSeconds(linkLifecycleOutboxStore.findOldestEligibleAt(), now),
                ageSeconds(linkLifecycleOutboxStore.findOldestParkedAt(), now),
                control.lastRequeueAt(),
                control.lastForceTickAt(),
                control.lastRelaySuccessAt(),
                control.lastRelayFailureAt(),
                control.lastRelayFailureReason());
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

    private LifecyclePipelineParkedRecordResponse toResponse(LinkLifecycleOutboxRecord record) {
        return new LifecyclePipelineParkedRecordResponse(
                record.id(),
                record.eventId(),
                record.eventType(),
                record.eventKey(),
                record.createdAt(),
                record.attemptCount(),
                record.lastErrorSummary(),
                record.parkedAt());
    }
}
