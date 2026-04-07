package com.linkplatform.api.owner.api;

import java.util.List;

public record CreateWebhookSubscriptionRequest(
        String name,
        String callbackUrl,
        List<String> eventTypes,
        Boolean enabled) {
}
