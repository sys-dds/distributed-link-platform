package com.linkplatform.api.link.application;

import java.util.Locale;

public enum LinkAbuseStatus {
    ACTIVE,
    FLAGGED,
    QUARANTINED;

    public static LinkAbuseStatus fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "flagged" -> FLAGGED;
            case "quarantined" -> QUARANTINED;
            default -> throw new IllegalArgumentException("Abuse must be one of: active, flagged, quarantined");
        };
    }
}
