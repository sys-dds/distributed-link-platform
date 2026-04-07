package com.linkplatform.api.runtime;

import com.linkplatform.api.link.application.AnalyticsOutboxRelay;
import com.linkplatform.api.link.application.AnalyticsOutboxStore;
import com.linkplatform.api.link.application.LinkLifecycleOutboxRelay;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.PipelineControl;
import com.linkplatform.api.link.application.PipelineControlStore;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

@Component("pipelineHealthIndicator")
public class PipelineHealthIndicator extends AbstractHealthIndicator {

    private final PipelineControlStore pipelineControlStore;
    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final Clock clock;

    public PipelineHealthIndicator(
            PipelineControlStore pipelineControlStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore) {
        this.pipelineControlStore = pipelineControlStore;
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.clock = Clock.systemUTC();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        builder.up().withDetail("analytics", pipelineDetails(
                        pipelineControlStore.get(AnalyticsOutboxRelay.PIPELINE_NAME),
                        analyticsOutboxStore.countEligible(),
                        analyticsOutboxStore.countParked(),
                        analyticsOutboxStore.findOldestEligibleAt(),
                        analyticsOutboxStore.findOldestParkedAt(),
                        now))
                .withDetail("lifecycle", pipelineDetails(
                        pipelineControlStore.get(LinkLifecycleOutboxRelay.PIPELINE_NAME),
                        linkLifecycleOutboxStore.countEligible(),
                        linkLifecycleOutboxStore.countParked(),
                        linkLifecycleOutboxStore.findOldestEligibleAt(),
                        linkLifecycleOutboxStore.findOldestParkedAt(),
                        now));
    }

    public PipelineSnapshot snapshot(String pipelineName) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return switch (pipelineName) {
            case AnalyticsOutboxRelay.PIPELINE_NAME -> toSnapshot(
                    pipelineControlStore.get(AnalyticsOutboxRelay.PIPELINE_NAME),
                    analyticsOutboxStore.countEligible(),
                    analyticsOutboxStore.countParked(),
                    analyticsOutboxStore.findOldestEligibleAt(),
                    analyticsOutboxStore.findOldestParkedAt(),
                    now);
            case LinkLifecycleOutboxRelay.PIPELINE_NAME -> toSnapshot(
                    pipelineControlStore.get(LinkLifecycleOutboxRelay.PIPELINE_NAME),
                    linkLifecycleOutboxStore.countEligible(),
                    linkLifecycleOutboxStore.countParked(),
                    linkLifecycleOutboxStore.findOldestEligibleAt(),
                    linkLifecycleOutboxStore.findOldestParkedAt(),
                    now);
            default -> throw new IllegalArgumentException("Unknown pipeline: " + pipelineName);
        };
    }

    private Map<String, Object> pipelineDetails(
            PipelineControl control,
            long eligibleCount,
            long parkedCount,
            OffsetDateTime oldestEligibleAt,
            OffsetDateTime oldestParkedAt,
            OffsetDateTime now) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paused", control.paused());
        details.put("pauseReason", control.pauseReason());
        details.put("eligibleCount", eligibleCount);
        details.put("parkedCount", parkedCount);
        details.put("oldestEligibleAgeSeconds", ageSeconds(oldestEligibleAt, now));
        details.put("oldestParkedAgeSeconds", ageSeconds(oldestParkedAt, now));
        details.put("lastRequeueAt", control.lastRequeueAt());
        details.put("lastForceTickAt", control.lastForceTickAt());
        details.put("lastRelaySuccessAt", control.lastRelaySuccessAt());
        details.put("lastRelayFailureAt", control.lastRelayFailureAt());
        details.put("lastRelayFailureReason", control.lastRelayFailureReason());
        return details;
    }

    private Double ageSeconds(OffsetDateTime timestamp, OffsetDateTime now) {
        if (timestamp == null) {
            return null;
        }
        return (double) Duration.between(timestamp, now).toSeconds();
    }

    private PipelineSnapshot toSnapshot(
            PipelineControl control,
            long eligibleCount,
            long parkedCount,
            OffsetDateTime oldestEligibleAt,
            OffsetDateTime oldestParkedAt,
            OffsetDateTime now) {
        return new PipelineSnapshot(
                control.paused(),
                eligibleCount,
                parkedCount,
                ageSeconds(oldestEligibleAt, now),
                ageSeconds(oldestParkedAt, now),
                control.lastRequeueAt(),
                control.lastForceTickAt(),
                control.lastRelaySuccessAt(),
                control.lastRelayFailureAt(),
                control.lastRelayFailureReason());
    }

    public record PipelineSnapshot(
            boolean paused,
            long eligibleCount,
            long parkedCount,
            Double oldestEligibleAgeSeconds,
            Double oldestParkedAgeSeconds,
            OffsetDateTime lastRequeueAt,
            OffsetDateTime lastForceTickAt,
            OffsetDateTime lastRelaySuccessAt,
            OffsetDateTime lastRelayFailureAt,
            String lastRelayFailureReason) {
    }
}
