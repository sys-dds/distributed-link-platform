package com.linkplatform.api.owner.api;

public record CreatedWebhookSubscriptionResponse(
        WebhookSubscriptionResponse subscription,
        String secret,
        String secretPrefix) {
}
