package com.linkplatform.api.runtime;

public record QueryReplicaProbeResult(
        boolean configured,
        boolean successful,
        String failureReason) {

    public static QueryReplicaProbeResult notConfigured() {
        return new QueryReplicaProbeResult(false, false, "dedicated query datasource not configured");
    }

    public static QueryReplicaProbeResult success() {
        return new QueryReplicaProbeResult(true, true, null);
    }

    public static QueryReplicaProbeResult failure(String failureReason) {
        return new QueryReplicaProbeResult(true, false, failureReason);
    }
}
