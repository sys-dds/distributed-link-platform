package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkspaceAccessServiceTest {

    private final WorkspacePermissionService workspacePermissionService = new WorkspacePermissionService();

    @Test
    void validatesRequestedScopesAgainstRole() {
        assertThat(workspacePermissionService.validateRequestedScopes(
                WorkspaceRole.EDITOR,
                List.of("links:read", "links:write", "analytics:read")))
                .containsExactlyInAnyOrder(
                        ApiKeyScope.LINKS_READ,
                        ApiKeyScope.LINKS_WRITE,
                        ApiKeyScope.ANALYTICS_READ);

        assertThatThrownBy(() -> workspacePermissionService.validateRequestedScopes(
                        WorkspaceRole.VIEWER,
                        List.of("links:write")))
                .isInstanceOf(WorkspaceScopeDeniedException.class);
    }

    @Test
    void grantedScopesAreIntersectionOfRoleAndApiKeyScopes() {
        Set<ApiKeyScope> granted = workspacePermissionService.grantedScopes(
                WorkspaceRole.ADMIN,
                Set.of(ApiKeyScope.LINKS_READ, ApiKeyScope.OPS_READ));

        assertThat(granted).containsExactlyInAnyOrder(ApiKeyScope.LINKS_READ, ApiKeyScope.OPS_READ);
    }
}
