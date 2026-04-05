package com.linkplatform.api.owner.application;

public record AuthenticatedOwner(
        long id,
        String ownerKey,
        String displayName,
        OwnerPlan plan) {
}
