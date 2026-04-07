package com.linkplatform.api.owner.api;

import java.util.List;

public record UpdateWebhookSubscriptionRequest(
        String name,
        Boolean enabled,
        List<String> eventTypes) {
}
