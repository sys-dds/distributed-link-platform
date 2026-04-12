package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WebhookEventPublisher {

    private final WebhookSubscriptionsStore webhookSubscriptionsStore;
    private final WebhookDeliveryStore webhookDeliveryStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookEventPublisher(
            WebhookSubscriptionsStore webhookSubscriptionsStore,
            WebhookDeliveryStore webhookDeliveryStore,
            ObjectMapper objectMapper,
            Clock clock) {
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.webhookDeliveryStore = webhookDeliveryStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void publish(long workspaceId, WebhookEventType eventType, String eventId, JsonNode payloadJson) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        JsonNode payload = withEventVersion(payloadJson);
        for (WebhookSubscriptionRecord subscription : webhookSubscriptionsStore.findEnabledByWorkspaceIdAndEventType(workspaceId, eventType)) {
            webhookDeliveryStore.create(
                    subscription.id(),
                    workspaceId,
                    eventType,
                    eventId,
                    payload,
                    WebhookDeliveryStatus.PENDING,
                    now,
                    now);
        }
    }

    @Transactional
    public void publish(long workspaceId, WebhookEventType eventType, String eventId, Object payload) {
        publish(workspaceId, eventType, eventId, objectMapper.valueToTree(payload));
    }

    private JsonNode withEventVersion(JsonNode payloadJson) {
        if (payloadJson instanceof ObjectNode objectNode) {
            ObjectNode copy = objectNode.deepCopy();
            if (!copy.has("eventVersion")) {
                copy.put("eventVersion", WebhookEventType.CURRENT_EVENT_VERSION);
            }
            return copy;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("eventVersion", WebhookEventType.CURRENT_EVENT_VERSION);
        wrapper.set("data", payloadJson);
        return wrapper;
    }
}
