package com.linkplatform.api.owner.api;

public record WorkspaceResponse(
        long id,
        String slug,
        String displayName,
        boolean personalWorkspace,
        String role) {
}
