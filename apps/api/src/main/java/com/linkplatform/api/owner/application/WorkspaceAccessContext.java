package com.linkplatform.api.owner.application;

import java.util.Set;

public record WorkspaceAccessContext(
        AuthenticatedOwner owner,
        long workspaceId,
        String workspaceSlug,
        String workspaceDisplayName,
        boolean personalWorkspace,
        WorkspaceRole role,
        Set<ApiKeyScope> grantedScopes,
        String apiKeyHash) {

    public long ownerId() {
        return owner.id();
    }

    public String ownerKey() {
        return owner.ownerKey();
    }

    public String displayName() {
        return owner.displayName();
    }

    public OwnerPlan plan() {
        return owner.plan();
    }
}
