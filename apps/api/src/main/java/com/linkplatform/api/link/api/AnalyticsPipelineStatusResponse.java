package com.linkplatform.api.link.api;

public record AnalyticsPipelineStatusResponse(
        long eligibleBacklogCount,
        long parkedCount,
        Double oldestEligibleAgeSeconds) {
}
