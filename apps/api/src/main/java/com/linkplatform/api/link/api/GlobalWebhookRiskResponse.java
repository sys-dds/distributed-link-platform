package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.GovernanceRollupStore;
import java.time.OffsetDateTime;
import java.util.List;

public record GlobalWebhookRiskResponse(List<Item> items) {

    public static GlobalWebhookRiskResponse from(List<GovernanceRollupStore.WebhookRiskRecord> records) {
        return new GlobalWebhookRiskResponse(records.stream().map(Item::from).toList());
    }

    public record Item(
            String workspaceSlug,
            long subscriptionId,
            String name,
            int consecutiveFailures,
            OffsetDateTime lastFailureAt,
            boolean disabled) {

        private static Item from(GovernanceRollupStore.WebhookRiskRecord record) {
            return new Item(
                    record.workspaceSlug(),
                    record.subscriptionId(),
                    record.name(),
                    record.consecutiveFailures(),
                    record.lastFailureAt(),
                    record.disabled());
        }
    }
}
