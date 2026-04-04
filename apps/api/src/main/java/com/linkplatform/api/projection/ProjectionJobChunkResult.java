package com.linkplatform.api.projection;

record ProjectionJobChunkResult(boolean completed, long processedCount, Long checkpointId) {
}
