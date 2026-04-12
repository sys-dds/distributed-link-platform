package com.linkplatform.api.runtime;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface QueryReplicaRuntimeStore {

    Optional<QueryReplicaRuntimeState> findByName(String replicaName);

    void recordFallback(
            String replicaName,
            String fallbackReason,
            String requestPath,
            Long workspaceId,
            OffsetDateTime triggeredAt,
            boolean appendLog);
}
