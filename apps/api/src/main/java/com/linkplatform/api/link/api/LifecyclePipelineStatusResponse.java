package com.linkplatform.api.link.api;

public record LifecyclePipelineStatusResponse(
        long eligibleBacklogCount,
        long parkedCount,
        Double oldestEligibleAgeSeconds) {
}
