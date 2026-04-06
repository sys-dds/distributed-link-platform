package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record DrainParkedQueueResponse(
        String pipelineName,
        int requestedLimit,
        int appliedLimit,
        int movedCount,
        long remainingParkedCount,
        OffsetDateTime lastRequeueAt) {
}
