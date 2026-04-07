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

    public static final int MAX_PAUSE_REASON_LENGTH = 255;
    public static final int MAX_FAILURE_REASON_LENGTH = 512;

    public PipelineControl {
        pauseReason = normalize(pauseReason, MAX_PAUSE_REASON_LENGTH);
        lastRelayFailureReason = normalize(lastRelayFailureReason, MAX_FAILURE_REASON_LENGTH);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
