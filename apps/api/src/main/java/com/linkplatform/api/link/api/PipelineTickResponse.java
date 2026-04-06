package com.linkplatform.api.link.api;

public record PipelineTickResponse(
        String pipelineName,
        boolean paused,
        int processedCount,
        int parkedCount,
        long eligibleCountAfter,
        long parkedCountAfter) {
}
