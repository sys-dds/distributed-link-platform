package com.linkplatform.api.runtime;

import java.time.OffsetDateTime;

public record QueryReplicaFallbackRecord(
        long id,
        String replicaName,
        String fallbackReason,
        String requestPath,
        Long workspaceId,
        OffsetDateTime triggeredAt) {
}
