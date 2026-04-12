package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.GovernanceRollupStore;
import java.util.List;

public record GlobalAbuseRiskResponse(List<Item> items) {

    public static GlobalAbuseRiskResponse from(List<GovernanceRollupStore.AbuseRiskRecord> records) {
        return new GlobalAbuseRiskResponse(records.stream().map(Item::from).toList());
    }

    public record Item(
            String workspaceSlug,
            String host,
            long signalCount,
            long openCases,
            long quarantinedLinks) {

        private static Item from(GovernanceRollupStore.AbuseRiskRecord record) {
            return new Item(
                    record.workspaceSlug(),
                    record.host(),
                    record.signalCount(),
                    record.openCases(),
                    record.quarantinedLinks());
        }
    }
}
