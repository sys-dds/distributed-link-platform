package com.linkplatform.api.owner.application;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkspacePermissionService {

    public Set<ApiKeyScope> grantedScopes(WorkspaceRole role, Set<ApiKeyScope> apiKeyScopes) {
        Set<ApiKeyScope> roleScopes = EnumSet.copyOf(role.impliedScopes());
        if (apiKeyScopes == null || apiKeyScopes.isEmpty()) {
            return role == WorkspaceRole.OWNER ? roleScopes : Set.copyOf(roleScopes);
        }
        Set<ApiKeyScope> granted = EnumSet.noneOf(ApiKeyScope.class);
        for (ApiKeyScope scope : apiKeyScopes) {
            if (roleScopes.contains(scope)) {
                granted.add(scope);
            }
        }
        return Set.copyOf(granted);
    }

    public void requireScope(WorkspaceAccessContext context, ApiKeyScope requiredScope) {
        if (!context.grantedScopes().contains(requiredScope)) {
            throw new WorkspaceScopeDeniedException("Scope denied for workspace action: " + requiredScope.value());
        }
    }

    public void requireAnyScope(WorkspaceAccessContext context, Set<ApiKeyScope> acceptedScopes) {
        for (ApiKeyScope acceptedScope : acceptedScopes) {
            if (context.grantedScopes().contains(acceptedScope)) {
                return;
            }
        }
        throw new WorkspaceScopeDeniedException("Scope denied for workspace action");
    }

    public void requireOpsRead(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.OPS_READ);
    }

    public void requireOpsWrite(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.OPS_WRITE);
    }

    public void requireWebhooksRead(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.WEBHOOKS_READ);
    }

    public void requireWebhooksWrite(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.WEBHOOKS_WRITE);
    }

    public void requireExportsRead(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.EXPORTS_READ);
    }

    public void requireExportsWrite(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.EXPORTS_WRITE);
    }

    public void requireRetentionRead(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.RETENTION_READ);
    }

    public void requireRetentionWrite(WorkspaceAccessContext context) {
        requireScope(context, ApiKeyScope.RETENTION_WRITE);
    }

    public Set<ApiKeyScope> validateRequestedScopes(WorkspaceRole role, List<String> requestedScopes) {
        Set<ApiKeyScope> parsed = EnumSet.noneOf(ApiKeyScope.class);
        if (requestedScopes != null) {
            for (String requestedScope : requestedScopes) {
                parsed.add(ApiKeyScope.fromValue(requestedScope));
            }
        }
        if (parsed.isEmpty()) {
            parsed.addAll(role.impliedScopes());
        }
        Set<ApiKeyScope> allowed = role.impliedScopes();
        for (ApiKeyScope requested : parsed) {
            if (!allowed.contains(requested)) {
                throw new WorkspaceScopeDeniedException("Requested API key scope exceeds caller role");
            }
        }
        return Set.copyOf(new LinkedHashSet<>(parsed));
    }
}
