package com.linkplatform.api.owner.application;

public enum OwnerPlan {
    FREE(2, 5, 10),
    PRO(100, 10, 20);

    private final int activeLinkLimit;
    private final int readRequestsPerMinute;
    private final int mutationRequestsPerMinute;

    OwnerPlan(int activeLinkLimit, int readRequestsPerMinute, int mutationRequestsPerMinute) {
        this.activeLinkLimit = activeLinkLimit;
        this.readRequestsPerMinute = readRequestsPerMinute;
        this.mutationRequestsPerMinute = mutationRequestsPerMinute;
    }

    public int activeLinkLimit() {
        return activeLinkLimit;
    }

    public int readRequestsPerMinute() {
        return readRequestsPerMinute;
    }

    public int mutationRequestsPerMinute() {
        return mutationRequestsPerMinute;
    }
}
