package com.linkplatform.api.owner.application;

import java.util.Arrays;
import java.util.Locale;

public enum WebhookEventType {
    LINK_CREATED("link.created"),
    LINK_UPDATED("link.updated"),
    LINK_DELETED("link.deleted"),
    LINK_LIFECYCLE_CHANGED("link.lifecycle.changed"),
    ABUSE_CASE_OPENED("abuse.case.opened"),
    LINK_QUARANTINED("link.quarantined"),
    LINK_RELEASED("link.released"),
    PROJECTION_JOB_COMPLETED("projection.job.completed"),
    WEBHOOK_VERIFICATION("webhook.verification"),
    WEBHOOK_TEST_FIRED("webhook.test-fired");

    public static final int CURRENT_EVENT_VERSION = 1;

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static WebhookEventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value == null ? null : value.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported webhook event type: " + value));
    }
}
