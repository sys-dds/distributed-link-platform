package com.linkplatform.api.link.api;

public record LifecyclePipelineStatusResponse(
        long eligibleCount,
        long parkedCount,
        Double oldestEligibleAgeSeconds,
        Double oldestParkedAgeSeconds,
        java.time.OffsetDateTime lastRequeueAt) {
}
