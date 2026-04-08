package com.linkplatform.api.owner.application;

import java.util.Locale;

public enum WorkspacePlanCode {
    FREE,
    PRO,
    ENTERPRISE;

    public static WorkspacePlanCode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("planCode is required");
        }
        return WorkspacePlanCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
