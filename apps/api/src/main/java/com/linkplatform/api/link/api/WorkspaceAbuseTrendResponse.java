package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.WorkspaceAbuseIntelligenceService;
import com.linkplatform.api.link.application.WorkspaceAbuseTrendRecord;
import java.time.OffsetDateTime;
import java.util.List;

public record WorkspaceAbuseTrendResponse(
        List<HostTrendResponse> topFlaggedHostsLast7d,
        List<HostTrendResponse> topQuarantinedHostsLast7d,
        long totalOpenAbuseCases,
        long totalQuarantinedLinks,
        OffsetDateTime latestUpdatedAt) {

    static WorkspaceAbuseTrendResponse from(WorkspaceAbuseIntelligenceService.AbuseTrendSummary summary) {
        return new WorkspaceAbuseTrendResponse(
                summary.topFlaggedHostsLast7d().stream().map(HostTrendResponse::from).toList(),
                summary.topQuarantinedHostsLast7d().stream().map(HostTrendResponse::from).toList(),
                summary.totalOpenAbuseCases(),
                summary.totalQuarantinedLinks(),
                summary.latestUpdatedAt());
    }

    public record HostTrendResponse(String host, long count, OffsetDateTime latestUpdatedAt) {

        static HostTrendResponse from(WorkspaceAbuseTrendRecord record) {
            return new HostTrendResponse(record.host(), record.count(), record.latestUpdatedAt());
        }
    }
}
