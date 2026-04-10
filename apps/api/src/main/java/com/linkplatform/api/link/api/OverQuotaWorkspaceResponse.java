package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.GovernanceRollupStore;
import java.util.List;

public record OverQuotaWorkspaceResponse(List<Item> items) {

    public static OverQuotaWorkspaceResponse from(List<GovernanceRollupStore.OverQuotaWorkspaceRecord> records) {
        return new OverQuotaWorkspaceResponse(records.stream().map(Item::from).toList());
    }

    public record Item(
            String workspaceSlug,
            String planCode,
            String metric,
            long currentUsage,
            long limit) {

        private static Item from(GovernanceRollupStore.OverQuotaWorkspaceRecord record) {
            return new Item(
                    record.workspaceSlug(),
                    record.planCode(),
                    record.metric(),
                    record.currentUsage(),
                    record.limit());
        }
    }
}
