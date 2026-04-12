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
            return ReplicaDecision.fallback("replica runtime state missing", null, null);
        }
        QueryReplicaRuntimeState current = state.get();
        if (!current.enabled()) {
            return ReplicaDecision.fallback("replica disabled", current.lagSeconds(), heartbeatAgeSeconds(current).orElse(null));
        }

        Long heartbeatAgeSeconds = heartbeatAgeSeconds(current).orElse(null);
        if (heartbeatAgeSeconds == null) {
            return ReplicaDecision.fallback("replica heartbeat missing", current.lagSeconds(), null);
        }
        if (heartbeatAgeSeconds > maxLagSeconds) {
            return ReplicaDecision.fallback("replica heartbeat stale", current.lagSeconds(), heartbeatAgeSeconds);
        }

        // lag_seconds is measured or operator-fed replica lag from the runtime table.
        // Heartbeat freshness proves the query datasource can answer, but it is not replication lag.
        Long lagSeconds = current.lagSeconds();
        if (lagSeconds != null && lagSeconds > maxLagSeconds) {
            return ReplicaDecision.fallback("replica stale", lagSeconds, heartbeatAgeSeconds);
        }
        return ReplicaDecision.replica(lagSeconds, heartbeatAgeSeconds);
    }

    public long maxLagSeconds() {
        return maxLagSeconds;
    }

    public Optional<Long> heartbeatAgeSeconds(QueryReplicaRuntimeState state) {
        if (state.lastHeartbeatAt() == null) {
            return Optional.empty();
        }
        long ageSeconds = Duration.between(state.lastHeartbeatAt(), OffsetDateTime.now(clock)).toSeconds();
        return Optional.of(Math.max(0L, ageSeconds));
    }

    public record ReplicaDecision(
            boolean useReplica,
            String fallbackReason,
            Long lagSeconds,
            Long heartbeatAgeSeconds) {

        static ReplicaDecision replica(Long lagSeconds, Long heartbeatAgeSeconds) {
            return new ReplicaDecision(true, null, lagSeconds, heartbeatAgeSeconds);
        }

        static ReplicaDecision fallback(String fallbackReason, Long lagSeconds, Long heartbeatAgeSeconds) {
            return new ReplicaDecision(false, fallbackReason, lagSeconds, heartbeatAgeSeconds);
        }
    }
}
