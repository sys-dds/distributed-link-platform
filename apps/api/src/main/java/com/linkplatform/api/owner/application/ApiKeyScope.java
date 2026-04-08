package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

public enum ApiKeyScope {
    LINKS_READ("links:read"),
    LINKS_WRITE("links:write"),
    ANALYTICS_READ("analytics:read"),
    API_KEYS_READ("api_keys:read"),
    API_KEYS_WRITE("api_keys:write"),
    // Plan and usage reads stay intentionally coupled to membership visibility.
    MEMBERS_READ("members:read"),
    MEMBERS_WRITE("members:write"),
    WEBHOOKS_READ("webhooks:read"),
    WEBHOOKS_WRITE("webhooks:write"),
    // Retention remains separate so editor/viewer-style keys do not gain accidental access.
    EXPORTS_READ("exports:read"),
    EXPORTS_WRITE("exports:write"),
    RETENTION_READ("retention:read"),
    RETENTION_WRITE("retention:write"),
    OPS_READ("ops:read"),
    OPS_WRITE("ops:write");

    private final String value;

    ApiKeyScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static ApiKeyScope fromValue(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(value == null ? null : value.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown API key scope: " + value));
    }
}
