package com.linkplatform.api.owner.api;

public record MeResponse(
        String ownerKey,
        String displayName,
        String plan,
        long activeLinkCount,
        long activeLinkLimit) {
}
