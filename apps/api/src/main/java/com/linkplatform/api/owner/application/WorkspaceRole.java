package com.linkplatform.api.owner.application;

import java.util.EnumSet;
import java.util.Set;

public enum WorkspaceRole {
    OWNER,
    ADMIN,
    EDITOR,
    VIEWER;

    public boolean ownerLike() {
        return this == OWNER;
    }

    public Set<ApiKeyScope> impliedScopes() {
        return switch (this) {
            case OWNER, ADMIN -> EnumSet.allOf(ApiKeyScope.class);
            case EDITOR -> EnumSet.of(
                    ApiKeyScope.LINKS_READ,
                    ApiKeyScope.LINKS_WRITE,
                    ApiKeyScope.ANALYTICS_READ);
            case VIEWER -> EnumSet.of(
                    ApiKeyScope.LINKS_READ,
                    ApiKeyScope.ANALYTICS_READ);
        };
    }
}
