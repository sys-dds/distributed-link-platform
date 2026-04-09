package com.linkplatform.api.link.application;

import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WebhookEventPublisher;
import com.linkplatform.api.owner.application.WebhookEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkAbuseReviewService {

    private final LinkAbuseStore linkAbuseStore;
    private final LinkStore linkStore;
    private final WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService;
    private final SecurityEventStore securityEventStore;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final Clock clock;
    private final WebhookEventPublisher webhookEventPublisher;

    public LinkAbuseReviewService(
            LinkAbuseStore linkAbuseStore,
            LinkStore linkStore,
            WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService,
            SecurityEventStore securityEventStore,
            LinkPlatformRuntimeProperties runtimeProperties,
            WebhookEventPublisher webhookEventPublisher,
            Clock clock) {
        this.linkAbuseStore = linkAbuseStore;
        this.linkStore = linkStore;
        this.workspaceAbuseIntelligenceService = workspaceAbuseIntelligenceService;
        this.securityEventStore = securityEventStore;
        this.runtimeProperties = runtimeProperties;
        this.webhookEventPublisher = webhookEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void rejectUnsafeTarget(WorkspaceAccessContext context, String slug, TargetRiskAssessment assessment) {
        record(SecurityEventType.LINK_TARGET_REJECTED, context.ownerId(), context.workspaceId(), slug, assessment.summary());
        throw new UnsafeLinkTargetException(assessment.summary());
    }

    @Transactional
    public LinkAbuseCaseRecord flagTargetForReview(WorkspaceAccessContext context, String slug, TargetRiskAssessment assessment) {
        OffsetDateTime now = now();
        LinkAbuseCaseRecord record = linkAbuseStore.openOrIncrementCase(
                context.workspaceId(),
                slug,
                LinkAbuseSource.TARGET_POLICY,
                assessment.riskScore(),
                assessment.summary(),
                assessment.summary(),
                assessment.normalizedTargetHost(),
                context.ownerId(),
                now);
        workspaceAbuseIntelligenceService.recordHostSignal(context.workspaceId(), assessment.normalizedTargetHost());
        linkStore.flagLinkForAbuse(context.workspaceId(), slug, assessment.summary(), now, context.ownerId(), null, true);
        autoQuarantineForRepeatedHost(context.workspaceId(), context.ownerId(), slug, assessment.normalizedTargetHost(), record, now);
        record(
                record.signalCount() == 1 ? SecurityEventType.ABUSE_CASE_OPENED : SecurityEventType.ABUSE_CASE_SIGNAL_INCREMENTED,
                context.ownerId(),
                context.workspaceId(),
                slug,
                record.summary());
        publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.ABUSE_CASE_OPENED, "abuse-case:" + record.id(), record);
        record(SecurityEventType.LINK_FLAGGED_FOR_REVIEW, context.ownerId(), context.workspaceId(), slug, assessment.summary());
        return record;
    }

    @Transactional
    public void recordQuarantinedRedirectAttempt(String slug, String requestPath, String remoteAddress) {
        linkAbuseStore.findLinkScope(slug).ifPresent(link -> securityEventStore.record(
                SecurityEventType.LINK_QUARANTINED_REDIRECT_ATTEMPT,
                link.ownerId(),
                link.workspaceId(),
                null,
                "GET",
                requestPath,
                remoteAddress,
                "Quarantined redirect blocked",
                now()));
    }

    @Transactional
    public void recordRedirectRateLimitSignal(String slug) {
        if (!runtimeProperties.getAbuse().isEnabled()) {
            return;
        }
        LinkAbuseStore.LinkScopeRecord link = linkAbuseStore.findLinkScope(slug).orElse(null);
        if (link == null) {
            return;
        }
        OffsetDateTime now = now();
        LinkAbuseCaseRecord record = linkAbuseStore.openOrIncrementCase(
                link.workspaceId(),
                slug,
                LinkAbuseSource.REDIRECT_RATE_LIMIT,
                70,
                "Redirect rate-limit abuse signals exceeded",
                "Repeated public redirect rate-limit rejections",
                link.hostname(),
                null,
                now);
        workspaceAbuseIntelligenceService.recordHostSignal(link.workspaceId(), link.hostname());
        record(
                record.signalCount() == 1 ? SecurityEventType.ABUSE_CASE_OPENED : SecurityEventType.ABUSE_CASE_SIGNAL_INCREMENTED,
                link.ownerId(),
                link.workspaceId(),
                slug,
                record.summary());
        if (link.abuseStatus() != LinkAbuseStatus.QUARANTINED) {
            linkStore.flagLinkForAbuse(link.workspaceId(), slug, record.summary(), now, null, null, true);
        }
        if (record.signalCount() >= workspaceAbuseIntelligenceService.redirectRateLimitThreshold(link.workspaceId())
                && link.abuseStatus() != LinkAbuseStatus.QUARANTINED) {
            linkStore.quarantineLink(link.workspaceId(), slug, record.summary(), now, null, null);
            linkAbuseStore.resolveCase(
                    link.workspaceId(),
                    record.id(),
                    LinkAbuseCaseStatus.OPEN,
                    LinkAbuseCaseStatus.QUARANTINED,
                    "QUARANTINE",
                    null,
                    null,
                    now);
            record(SecurityEventType.LINK_QUARANTINED, link.ownerId(), link.workspaceId(), slug, record.summary());
        }
    }

    @Transactional(readOnly = true)
    public AbuseReviewPage listQueue(WorkspaceAccessContext context, LinkAbuseQueueQuery query) {
        List<LinkAbuseCaseRecord> fetched = linkAbuseStore.findQueue(context.workspaceId(), query);
        boolean hasMore = fetched.size() > query.limit();
        List<LinkAbuseCaseRecord> items = hasMore ? fetched.subList(0, query.limit()) : fetched;
        String nextCursor = hasMore ? JdbcLinkAbuseStore.encodeCursor(items.get(items.size() - 1)) : null;
        return new AbuseReviewPage(items, nextCursor, hasMore);
    }

    @Transactional
    public LinkAbuseCaseRecord createManualCase(
            WorkspaceAccessContext context,
            String slug,
            String summary,
            String detailSummary,
            Integer riskScore,
            boolean quarantineNow) {
        requireLinkInWorkspace(context.workspaceId(), slug);
        OffsetDateTime now = now();
        LinkAbuseCaseRecord record = linkAbuseStore.openOrIncrementCase(
                context.workspaceId(),
                slug,
                LinkAbuseSource.MANUAL_OPERATOR,
                riskScore == null ? 50 : riskScore,
                summary,
                detailSummary,
                null,
                context.ownerId(),
                now);
        String host = linkAbuseStore.findLinkScope(slug).map(LinkAbuseStore.LinkScopeRecord::hostname).orElse(null);
        workspaceAbuseIntelligenceService.recordHostSignal(context.workspaceId(), host);
        record(
                record.signalCount() == 1 ? SecurityEventType.ABUSE_CASE_OPENED : SecurityEventType.ABUSE_CASE_SIGNAL_INCREMENTED,
                context.ownerId(),
                context.workspaceId(),
                slug,
                record.summary());
        autoQuarantineForRepeatedHost(context.workspaceId(), context.ownerId(), slug, host, record, now);
        if (quarantineNow) {
            return quarantineCase(context, record.id(), null);
        }
        linkStore.flagLinkForAbuse(context.workspaceId(), slug, summary, now, context.ownerId(), null, true);
        record(SecurityEventType.LINK_FLAGGED_FOR_REVIEW, context.ownerId(), context.workspaceId(), slug, summary);
        return record;
    }

    @Transactional
    public LinkAbuseCaseRecord quarantineCase(WorkspaceAccessContext context, long caseId, String resolutionNote) {
        return resolveCase(context, caseId, LinkAbuseCaseStatus.QUARANTINED, "QUARANTINE", resolutionNote);
    }

    @Transactional
    public LinkAbuseCaseRecord releaseCase(WorkspaceAccessContext context, long caseId, String resolutionNote) {
        return resolveCase(context, caseId, LinkAbuseCaseStatus.RELEASED, "RELEASE", resolutionNote);
    }

    @Transactional
    public LinkAbuseCaseRecord dismissCase(WorkspaceAccessContext context, long caseId, String resolutionNote) {
        return resolveCase(context, caseId, LinkAbuseCaseStatus.DISMISSED, "DISMISS", resolutionNote);
    }

    private LinkAbuseCaseRecord resolveCase(
            WorkspaceAccessContext context,
            long caseId,
            LinkAbuseCaseStatus nextStatus,
            String resolution,
            String resolutionNote) {
        LinkAbuseCaseRecord existing = linkAbuseStore.findCaseById(context.workspaceId(), caseId)
                .orElseThrow(() -> new AbuseCaseNotFoundException(caseId));
        if (existing.status() != LinkAbuseCaseStatus.OPEN) {
            throw new InvalidAbuseCaseTransitionException("Abuse case transition requires an OPEN case");
        }
        OffsetDateTime now = now();
        if (!linkAbuseStore.resolveCase(
                context.workspaceId(),
                caseId,
                LinkAbuseCaseStatus.OPEN,
                nextStatus,
                resolution,
                context.ownerId(),
                resolutionNote,
                now)) {
            throw new InvalidAbuseCaseTransitionException("Abuse case transition could not be applied");
        }
        if (nextStatus == LinkAbuseCaseStatus.QUARANTINED) {
            linkStore.quarantineLink(context.workspaceId(), existing.slug(), existing.summary(), now, context.ownerId(), resolutionNote);
            record(SecurityEventType.LINK_QUARANTINED, context.ownerId(), context.workspaceId(), existing.slug(), existing.summary());
            publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_QUARANTINED, "abuse-case:" + existing.id() + ":quarantined", existing);
        } else if (nextStatus == LinkAbuseCaseStatus.RELEASED) {
            linkStore.releaseLink(context.workspaceId(), existing.slug(), context.ownerId(), resolutionNote, now);
            record(SecurityEventType.LINK_RELEASED, context.ownerId(), context.workspaceId(), existing.slug(), existing.summary());
            publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_RELEASED, "abuse-case:" + existing.id() + ":released", existing);
        } else {
            LinkAbuseStatus currentStatus = linkStore.findAbuseStatusBySlug(existing.slug(), context.workspaceId())
                    .orElse(LinkAbuseStatus.ACTIVE);
            if (currentStatus == LinkAbuseStatus.FLAGGED && linkAbuseStore.countOpenCases(context.workspaceId(), existing.slug()) == 0) {
                linkStore.clearFlaggedLink(context.workspaceId(), existing.slug(), context.ownerId(), resolutionNote, now);
            }
            record(SecurityEventType.LINK_ABUSE_DISMISSED, context.ownerId(), context.workspaceId(), existing.slug(), existing.summary());
        }
        return linkAbuseStore.findCaseById(context.workspaceId(), caseId).orElseThrow(() -> new AbuseCaseNotFoundException(caseId));
    }

    private void requireLinkInWorkspace(long workspaceId, String slug) {
        if (linkStore.findStoredDetailsBySlug(slug, workspaceId).isEmpty()) {
            throw new LinkNotFoundException(slug);
        }
    }

    private void autoQuarantineForRepeatedHost(
            long workspaceId,
            Long ownerId,
            String slug,
            String normalizedHost,
            LinkAbuseCaseRecord record,
            OffsetDateTime now) {
        if (normalizedHost == null
                || !workspaceAbuseIntelligenceService.repeatedHostThresholdReached(workspaceId, normalizedHost)) {
            return;
        }
        LinkAbuseStatus currentStatus = linkStore.findAbuseStatusBySlug(slug, workspaceId).orElse(LinkAbuseStatus.ACTIVE);
        if (currentStatus == LinkAbuseStatus.QUARANTINED) {
            return;
        }
        linkStore.quarantineLink(workspaceId, slug, record.summary(), now, ownerId, null);
        linkAbuseStore.resolveCase(
                workspaceId,
                record.id(),
                LinkAbuseCaseStatus.OPEN,
                LinkAbuseCaseStatus.QUARANTINED,
                "QUARANTINE",
                ownerId,
                null,
                now);
        record(SecurityEventType.LINK_QUARANTINED, ownerId, workspaceId, slug, record.summary());
    }

    private void record(SecurityEventType eventType, Long ownerId, Long workspaceId, String slug, String detail) {
        securityEventStore.record(
                eventType,
                ownerId,
                workspaceId,
                null,
                "POST",
                slug == null ? "/api/v1/ops/abuse/reviews" : "/api/v1/ops/abuse/reviews/" + slug,
                null,
                detail,
                now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private void publishWebhook(long workspaceId, String workspaceSlug, WebhookEventType eventType, String eventId, Object payload) {
        webhookEventPublisher.publish(workspaceId, eventType, eventId, payload);
    }

    public record AbuseReviewPage(List<LinkAbuseCaseRecord> items, String nextCursor, boolean hasMore) {
    }

    @Transactional(readOnly = true)
    public long countCasesByStatus(long workspaceId, LinkAbuseCaseStatus status) {
        return linkAbuseStore.countCasesByStatus(workspaceId, status);
    }

    @Transactional(readOnly = true)
    public long countCasesResolvedOnDay(long workspaceId, LinkAbuseCaseStatus status, LocalDate day) {
        return linkAbuseStore.countCasesResolvedOnDay(workspaceId, status, day);
    }

    @Transactional(readOnly = true)
    public OffsetDateTime findLatestUpdatedAt(long workspaceId) {
        return linkAbuseStore.findLatestUpdatedAt(workspaceId).orElse(null);
    }
}
