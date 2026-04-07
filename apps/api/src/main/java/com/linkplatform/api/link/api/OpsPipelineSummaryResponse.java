package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record OpsPipelineSummaryResponse(
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
