package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkAbuseStore {

    LinkAbuseCaseRecord openOrIncrementCase(
            long workspaceId,
            String slug,
            LinkAbuseSource source,
            int riskScore,
            String summary,
            String detailSummary,
            String targetHost,
            Long createdByOwnerId,
            OffsetDateTime now);

    Optional<LinkAbuseCaseRecord> findCaseById(long workspaceId, long caseId);

    Optional<LinkAbuseCaseRecord> findOpenCase(long workspaceId, String slug, LinkAbuseSource source);

    List<LinkAbuseCaseRecord> findQueue(long workspaceId, LinkAbuseQueueQuery query);

    Optional<LinkScopeRecord> findLinkScope(String slug);

    long countOpenCases(long workspaceId, String slug);

    long countCasesByStatus(long workspaceId, LinkAbuseCaseStatus status);

    long countCasesResolvedOnDay(long workspaceId, LinkAbuseCaseStatus status, java.time.LocalDate day);

    Optional<OffsetDateTime> findLatestUpdatedAt(long workspaceId);

    boolean resolveCase(
            long workspaceId,
            long caseId,
            LinkAbuseCaseStatus currentStatus,
            LinkAbuseCaseStatus nextStatus,
            String resolution,
            Long reviewedByOwnerId,
            String resolutionNote,
            OffsetDateTime now);

    record LinkScopeRecord(
            long workspaceId,
            long ownerId,
            String slug,
            String hostname,
            LinkAbuseStatus abuseStatus) {
    }
}
