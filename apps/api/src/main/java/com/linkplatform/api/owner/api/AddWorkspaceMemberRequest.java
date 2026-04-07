package com.linkplatform.api.owner.api;

public record AddWorkspaceMemberRequest(
        long ownerId,
        String role) {
}
