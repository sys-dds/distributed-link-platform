package com.linkplatform.api.owner.api;

import java.util.List;

public record CurrentWorkspaceResponse(
        long id,
        String slug,
        String displayName,
        boolean personalWorkspace,
        String role,
        List<String> grantedScopes) {
}
