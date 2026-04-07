package com.linkplatform.api.owner.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WebhookEventPublisher {

    private WebhookSubscriptionsService webhookSubscriptionsService;

    @Autowired
    void setWebhookSubscriptionsService(WebhookSubscriptionsService webhookSubscriptionsService) {
        this.webhookSubscriptionsService = webhookSubscriptionsService;
    }

    public void publish(long workspaceId, String workspaceSlug, WebhookEventType eventType, String eventId, Object payload) {
        if (webhookSubscriptionsService == null) {
            return;
        }
        webhookSubscriptionsService.publish(workspaceId, workspaceSlug, eventType, eventId, payload);
    }
}
