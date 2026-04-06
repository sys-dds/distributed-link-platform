package com.linkplatform.api.projection;

record ProjectionJobChunkResult(
        boolean completed,
        long processedCount,
        Long checkpointId,
        long driftCount,
        long repairCount) {

    ProjectionJobChunkResult(boolean completed, long processedCount, Long checkpointId) {
        this(completed, processedCount, checkpointId, 0L, 0L);
    }
}
