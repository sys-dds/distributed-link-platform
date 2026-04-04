package com.linkplatform.api.owner.application;

public enum OwnerPlan {
    FREE(2),
    PRO(1_000);

    private final int activeLinkLimit;

    OwnerPlan(int activeLinkLimit) {
        this.activeLinkLimit = activeLinkLimit;
    }

    public int activeLinkLimit() {
        return activeLinkLimit;
    }
}
