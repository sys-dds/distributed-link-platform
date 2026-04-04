package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.AnalyticsOutboxRecord;
import com.linkplatform.api.link.application.AnalyticsOutboxStore;
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
@RequestMapping("/api/v1/analytics/pipeline")
public class AnalyticsPipelineController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final Clock clock;

    public AnalyticsPipelineController(AnalyticsOutboxStore analyticsOutboxStore) {
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.clock = Clock.systemUTC();
    }

    @GetMapping
    public AnalyticsPipelineStatusResponse getStatus() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new AnalyticsPipelineStatusResponse(
                analyticsOutboxStore.countEligible(now),
                analyticsOutboxStore.countParked(),
                analyticsOutboxStore.findOldestEligibleAgeSeconds(now));
    }

    @GetMapping("/parked")
    public List<AnalyticsPipelineParkedRecordResponse> getParked(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        validateLimit(limit);
        return analyticsOutboxStore.findParked(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/parked/{id}/requeue")
    public ResponseEntity<Void> requeueParked(@PathVariable long id) {
        if (!analyticsOutboxStore.requeueParked(id, OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parked analytics outbox row not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
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
