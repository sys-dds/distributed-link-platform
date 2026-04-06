package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record PipelineControl(
        String pipelineName,
        boolean paused,
        String pauseReason,
        OffsetDateTime updatedAt,
        OffsetDateTime lastForceTickAt,
        OffsetDateTime lastRequeueAt,
        OffsetDateTime lastRelaySuccessAt,
        OffsetDateTime lastRelayFailureAt,
        String lastRelayFailureReason) {
}
