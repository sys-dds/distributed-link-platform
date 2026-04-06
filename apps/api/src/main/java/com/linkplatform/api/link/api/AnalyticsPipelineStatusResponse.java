package com.linkplatform.api.link.api;

public record AnalyticsPipelineStatusResponse(
        String pipelineName,
        boolean paused,
        String pauseReason,
        long eligibleCount,
        long parkedCount,
        Double oldestEligibleAgeSeconds,
        Double oldestParkedAgeSeconds,
        java.time.OffsetDateTime lastRequeueAt,
        java.time.OffsetDateTime lastForceTickAt,
        java.time.OffsetDateTime lastRelaySuccessAt,
        java.time.OffsetDateTime lastRelayFailureAt,
        String lastRelayFailureReason) {
}
