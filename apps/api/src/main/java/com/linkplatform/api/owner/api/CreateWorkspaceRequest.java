package com.linkplatform.api.owner.api;

public record CreateWorkspaceRequest(
        String slug,
        String displayName) {
}
