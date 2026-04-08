package com.linkplatform.api.owner.api;

public record CreatedWorkspaceInvitationResponse(
        WorkspaceInvitationResponse invitation,
        String invitationToken) {
}
