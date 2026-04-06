package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkLifecycleOutboxRecord;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
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

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final OwnerAccessService ownerAccessService;
    private final Clock clock;

    public LifecyclePipelineController(
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            OwnerAccessService ownerAccessService) {
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.ownerAccessService = ownerAccessService;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    public LifecyclePipelineStatusResponse getStatus(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new LifecyclePipelineStatusResponse(
                linkLifecycleOutboxStore.countEligible(now),
                linkLifecycleOutboxStore.countParked(),
                linkLifecycleOutboxStore.findOldestEligibleAgeSeconds(now));
    }

    @GetMapping("/parked")
    public List<LifecyclePipelineParkedRecordResponse> getParked(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
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
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeMutation(
                apiKey,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        if (!linkLifecycleOutboxStore.requeueParked(id, OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parked lifecycle outbox row not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
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
