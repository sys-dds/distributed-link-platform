package com.linkplatform.api.owner.api;

import java.util.List;

public record MeResponse(
        String ownerKey,
        String displayName,
        String plan,
        long activeLinkCount,
        long activeLinkLimit,
        String activeWorkspaceSlug,
        String activeWorkspaceRole,
        List<String> activeWorkspaceScopes,
        String activeWorkspaceStatus,
        boolean activeWorkspaceSuspended,
        String personalWorkspaceSlug) {

    public MeResponse(
            String ownerKey,
            String displayName,
            String plan,
            long activeLinkCount,
            long activeLinkLimit) {
        this(ownerKey, displayName, plan, activeLinkCount, activeLinkLimit, null, null, List.of(), null, false, null);
    }
}
