package com.linkplatform.api.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

public class QueryReplicaLagPolicy {

    private final long maxLagSeconds;
    private final Clock clock;

    public QueryReplicaLagPolicy(long maxLagSeconds, Clock clock) {
        if (maxLagSeconds <= 0) {
            throw new IllegalArgumentException("maxLagSeconds must be greater than 0");
        }
        this.maxLagSeconds = maxLagSeconds;
        this.clock = clock;
    }

    public ReplicaDecision evaluate(Optional<QueryReplicaRuntimeState> state) {
        if (state.isEmpty()) {
            return ReplicaDecision.fallback("replica runtime state missing", null);
        }
        QueryReplicaRuntimeState current = state.get();
        if (!current.enabled()) {
            return ReplicaDecision.fallback("replica disabled", current.lagSeconds());
        }
        Long lagSeconds = current.lagSeconds();
        if (lagSeconds == null && current.lastReplicaVisibleEventAt() != null) {
            lagSeconds = Math.max(0L, Duration.between(current.lastReplicaVisibleEventAt(), OffsetDateTime.now(clock)).toSeconds());
        }
        if (lagSeconds == null) {
            return ReplicaDecision.fallback("replica lag unknown", null);
        }
        if (lagSeconds > maxLagSeconds) {
            return ReplicaDecision.fallback("replica stale", lagSeconds);
        }
        return ReplicaDecision.replica(lagSeconds);
    }

    public long maxLagSeconds() {
        return maxLagSeconds;
    }

    public record ReplicaDecision(boolean useReplica, String fallbackReason, Long lagSeconds) {

        static ReplicaDecision replica(Long lagSeconds) {
            return new ReplicaDecision(true, null, lagSeconds);
        }

        static ReplicaDecision fallback(String fallbackReason, Long lagSeconds) {
            return new ReplicaDecision(false, fallbackReason, lagSeconds);
        }
    }
}
