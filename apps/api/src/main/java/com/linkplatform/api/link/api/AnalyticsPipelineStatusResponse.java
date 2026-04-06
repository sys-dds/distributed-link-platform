package com.linkplatform.api.link.api;

public record AnalyticsPipelineStatusResponse(
        long eligibleCount,
        long parkedCount,
        Double oldestEligibleAgeSeconds,
        Double oldestParkedAgeSeconds,
        java.time.OffsetDateTime lastRequeueAt) {
}
