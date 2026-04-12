package com.linkplatform.api.runtime;

import java.time.OffsetDateTime;

public record QueryReplicaRuntimeState(
        String replicaName,
        boolean enabled,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime lastReplicaVisibleEventAt,
        OffsetDateTime lastFallbackAt,
        String lastFallbackReason,
        Long lagSeconds,
        OffsetDateTime updatedAt) {
}
