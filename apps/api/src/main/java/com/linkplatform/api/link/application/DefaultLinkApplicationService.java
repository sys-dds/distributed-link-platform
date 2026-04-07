package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import com.linkplatform.api.owner.application.OwnerStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WebhookEventPublisher;
import com.linkplatform.api.owner.application.WebhookEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceEntitlementService;
import com.linkplatform.api.owner.application.WorkspaceQuotaExceededException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private static final Set<String> RESERVED_TOP_LEVEL_SLUGS = Set.of("api", "actuator", "error");
    private static final String CREATE_OPERATION = "CREATE";
    private static final String UPDATE_OPERATION = "UPDATE";
    private static final String DELETE_OPERATION = "DELETE";
    private static final String LIFECYCLE_OPERATION = "LIFECYCLE";
    private static final String BULK_OPERATION_PREFIX = "BULK:";
    private static final int MAX_ANALYTICS_LIMIT = 100;

    private final LinkStore linkStore;
    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final LinkMutationIdempotencyStore linkMutationIdempotencyStore;
    private final OwnerStore ownerStore;
    private final SecurityEventStore securityEventStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final LinkTargetPolicyService linkTargetPolicyService;
    private final LinkAbuseReviewService linkAbuseReviewService;
    private final LinkReadCache linkReadCache;
    private final URI publicBaseUri;
    private final Clock clock;
    private final WebhookEventPublisher webhookEventPublisher;

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkMutationIdempotencyStore linkMutationIdempotencyStore,
            OwnerStore ownerStore,
            SecurityEventStore securityEventStore,
            LinkReadCache linkReadCache,
            String publicBaseUrl) {
        this(
                linkStore,
                analyticsOutboxStore,
                linkLifecycleOutboxStore,
                linkMutationIdempotencyStore,
                ownerStore,
                securityEventStore,
                new LinkTargetPolicyService(),
                null,
                linkReadCache,
                publicBaseUrl);
    }

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkMutationIdempotencyStore linkMutationIdempotencyStore,
            OwnerStore ownerStore,
            SecurityEventStore securityEventStore,
            LinkTargetPolicyService linkTargetPolicyService,
            LinkAbuseReviewService linkAbuseReviewService,
            LinkReadCache linkReadCache,
            String publicBaseUrl) {
        this.linkStore = linkStore;
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkMutationIdempotencyStore = linkMutationIdempotencyStore;
        this.ownerStore = ownerStore;
        this.securityEventStore = securityEventStore;
        this.workspaceEntitlementService = null;
        this.webhookEventPublisher = null;
        this.linkTargetPolicyService = linkTargetPolicyService;
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.linkReadCache = linkReadCache;
        this.publicBaseUri = URI.create(publicBaseUrl);
            this.clock = Clock.systemUTC();
    }

    @Autowired
    public DefaultLinkApplicationService(
            LinkStore linkStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkMutationIdempotencyStore linkMutationIdempotencyStore,
            OwnerStore ownerStore,
            SecurityEventStore securityEventStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            WebhookEventPublisher webhookEventPublisher,
            LinkTargetPolicyService linkTargetPolicyService,
            LinkAbuseReviewService linkAbuseReviewService,
            LinkReadCache linkReadCache,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkMutationIdempotencyStore = linkMutationIdempotencyStore;
        this.ownerStore = ownerStore;
        this.securityEventStore = securityEventStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.linkTargetPolicyService = linkTargetPolicyService;
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.linkReadCache = linkReadCache;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public LinkMutationResult createLink(WorkspaceAccessContext context, CreateLinkCommand command, String idempotencyKey) {
        OffsetDateTime now = now();
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());
        rejectSelfTargetUrl(link.originalUrl());
        TargetRiskAssessment targetRiskAssessment = linkTargetPolicyService.assess(link.originalUrl().value());
        if (targetRiskAssessment.reject()) {
            rejectUnsafeTarget(context, link.slug().value(), targetRiskAssessment);
        }
        String normalizedTitle = normalizeTitle(command.title());
        List<String> normalizedTags = normalizeTags(command.tags());
        String hostname = extractHostname(link.originalUrl().value());
        String requestHash = fingerprintCreate(command.slug(), command.originalUrl(), command.expiresAt(), normalizedTitle, normalizedTags);
        LinkMutationResult replayed = findReplayIfPresent(context, idempotencyKey, CREATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        ownerStore.lockById(context.ownerId());
        enforceCreateQuota(context, now);

        if (!linkStore.save(link, command.expiresAt(), normalizedTitle, normalizedTags, hostname, 1L, context.workspaceId())) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }
        if (targetRiskAssessment.review()) {
            flagTargetForReview(context, link.slug().value(), targetRiskAssessment);
        }
        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(link.slug().value(), context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(link.slug().value()));
        LinkLifecycleEvent lifecycleEvent = new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                LinkLifecycleEventType.CREATED,
                context.ownerId(),
                context.workspaceId(),
                storedDetails.slug(),
                storedDetails.originalUrl(),
                storedDetails.title(),
                storedDetails.tags(),
                storedDetails.hostname(),
                storedDetails.expiresAt(),
                LinkLifecycleState.ACTIVE,
                storedDetails.version(),
                now);
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(lifecycleEvent);
        applyLifecycleReadModels(lifecycleEvent);
        LinkMutationResult result = LinkMutationResult.fromDetails(storedDetails);
        saveIdempotentResult(context, idempotencyKey, CREATE_OPERATION, requestHash, result, now);
        recordActiveLinksSnapshot(context.workspaceId(), result.slug(), now, "link_create");
        publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_CREATED, "link-created:" + result.slug() + ":" + result.version(), result);
        invalidateWriteCaches(context.workspaceId(), link.slug().value());
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult updateLink(
            WorkspaceAccessContext context,
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion,
            String idempotencyKey) {
        OffsetDateTime now = now();
        LinkSlug linkSlug = new LinkSlug(slug);
        OriginalUrl validatedOriginalUrl = new OriginalUrl(originalUrl);
        rejectSelfTargetUrl(validatedOriginalUrl);
        TargetRiskAssessment targetRiskAssessment = linkTargetPolicyService.assess(validatedOriginalUrl.value());
        if (targetRiskAssessment.reject()) {
            rejectUnsafeTarget(context, linkSlug.value(), targetRiskAssessment);
        }
        String normalizedTitle = normalizeTitle(title);
        List<String> normalizedTags = normalizeTags(tags);
        String hostname = extractHostname(validatedOriginalUrl.value());
        String requestHash = fingerprintUpdate(
                linkSlug.value(),
                validatedOriginalUrl.value(),
                expiresAt,
                normalizedTitle,
                normalizedTags,
                expectedVersion);
        LinkMutationResult replayed = findReplayIfPresent(context, idempotencyKey, UPDATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails beforeUpdate = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        long nextVersion = beforeUpdate.version() + 1;
        if (!linkStore.update(
                linkSlug.value(),
                validatedOriginalUrl.value(),
                expiresAt,
                normalizedTitle,
                normalizedTags,
                hostname,
                expectedVersion,
                nextVersion,
                context.workspaceId())) {
            throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
        }
        if (targetRiskAssessment.review()) {
            flagTargetForReview(context, linkSlug.value(), targetRiskAssessment);
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        LinkLifecycleEvent lifecycleEvent = toLifecycleEvent(
                determineUpdateEventType(beforeUpdate, storedDetails),
                context.ownerId(),
                context.workspaceId(),
                storedDetails,
                now);
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(lifecycleEvent);
        applyLifecycleReadModels(lifecycleEvent);
        LinkDetails visibleDetails = linkStore.findDetailsBySlug(linkSlug.value(), now, context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        LinkMutationResult result = LinkMutationResult.fromDetails(visibleDetails);
        saveIdempotentResult(context, idempotencyKey, UPDATE_OPERATION, requestHash, result, now);
        publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_UPDATED, "link-updated:" + result.slug() + ":" + result.version(), result);
        invalidateWriteCaches(context.workspaceId(), linkSlug.value());
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult deleteLink(WorkspaceAccessContext context, String slug, long expectedVersion, String idempotencyKey) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        String requestHash = fingerprintDelete(linkSlug.value(), expectedVersion);
        LinkMutationResult replayed = findReplayIfPresent(context, idempotencyKey, DELETE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        LinkLifecycleState currentState = resolveCurrentState(storedDetails, context.workspaceId(), now);

        if (!linkStore.deleteBySlug(linkSlug.value(), expectedVersion, context.workspaceId())) {
            throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
        }
        LinkMutationResult result = new LinkMutationResult(
                storedDetails.slug(),
                storedDetails.originalUrl(),
                storedDetails.createdAt(),
                storedDetails.expiresAt(),
                storedDetails.title(),
                storedDetails.tags(),
                storedDetails.hostname(),
                storedDetails.abuseStatus(),
                storedDetails.version() + 1,
                true);
        LinkLifecycleEvent lifecycleEvent = toLifecycleEvent(
                LinkLifecycleEventType.DELETED,
                context.ownerId(),
                context.workspaceId(),
                result,
                persistedStateFor(currentState),
                now);
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(lifecycleEvent);
        applyLifecycleReadModels(lifecycleEvent);
        saveIdempotentResult(context, idempotencyKey, DELETE_OPERATION, requestHash, result, now);
        publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_DELETED, "link-deleted:" + result.slug() + ":" + result.version(), result);
        invalidateWriteCaches(context.workspaceId(), linkSlug.value());
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult changeLifecycle(
            WorkspaceAccessContext context,
            String slug,
            String action,
            OffsetDateTime expiresAt,
            long expectedVersion,
            String idempotencyKey) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        LifecycleAction lifecycleAction = parseLifecycleAction(action);
        String requestHash = fingerprintLifecycle(linkSlug.value(), lifecycleAction, expiresAt, expectedVersion);
        LinkMutationResult replayed = findReplayIfPresent(context, idempotencyKey, LIFECYCLE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkMutationResult result = null;
        LinkLifecycleEvent lifecycleEvent = null;
        LinkStore.DeletedLinkSnapshot deletedSnapshot = linkStore.findDeletedSnapshotBySlug(linkSlug.value(), context.workspaceId()).orElse(null);
        LinkDetails current = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId()).orElse(null);
        LinkLifecycleState currentState = resolveCurrentState(current, deletedSnapshot, context.workspaceId(), linkSlug.value(), now);
        validateLifecycleAction(currentState, lifecycleAction);
        switch (lifecycleAction) {
            case RESTORE -> {
                if (deletedSnapshot == null) {
                    throw new LinkNotFoundException(linkSlug.value());
                }
                if (deletedSnapshot.version() != expectedVersion) {
                    throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
                }
                if (restoreTargetState(deletedSnapshot, now) == LinkLifecycleState.ACTIVE) {
                    enforceCreateQuota(context, now);
                }
                LinkLifecycleState restoredState = restoreTargetState(deletedSnapshot, now);
                long nextVersion = deletedSnapshot.version() + 1;
                if (!linkStore.restoreDeleted(deletedSnapshot, persistedStateFor(restoredState), nextVersion, context.workspaceId())) {
                    throw new LinkMutationConflictException("Deleted link cannot be restored for slug: " + linkSlug.value());
                }
                LinkDetails restored = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId())
                        .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
                result = LinkMutationResult.fromDetails(restored);
                recordActiveLinksSnapshot(context.workspaceId(), restored.slug(), now, "link_restore");
                lifecycleEvent = toLifecycleEvent(LinkLifecycleEventType.RESTORED, context.ownerId(), context.workspaceId(), restored, persistedStateFor(restoredState), now);
            }
            case EXPIRE_NOW, EXTEND_EXPIRY, SUSPEND, RESUME, ARCHIVE, UNARCHIVE -> {
                if (current == null) {
                    throw new LinkNotFoundException(linkSlug.value());
                }
                OffsetDateTime nextExpiresAt = resolveNextExpiry(current, lifecycleAction, expiresAt, now);
                LinkLifecycleState nextState = resolveNextState(currentState, lifecycleAction, nextExpiresAt, now);
                long nextVersion = current.version() + 1;
                if (!linkStore.updateLifecycle(
                        linkSlug.value(),
                        persistedStateFor(currentState),
                        persistedStateFor(nextState),
                        nextExpiresAt,
                        expectedVersion,
                        nextVersion,
                        context.workspaceId())) {
                    throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
                }
                LinkDetails updated = linkStore.findStoredDetailsBySlug(linkSlug.value(), context.workspaceId())
                        .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
                result = LinkMutationResult.fromDetails(updated);
                lifecycleEvent = toLifecycleEvent(determineLifecycleEventType(lifecycleAction), context.ownerId(), context.workspaceId(), updated, persistedStateFor(nextState), now);
            }
        }
        if (result == null || lifecycleEvent == null) {
            throw new IllegalStateException("Lifecycle change did not produce a result");
        }
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(lifecycleEvent);
        applyLifecycleReadModels(lifecycleEvent);
        saveIdempotentResult(context, idempotencyKey, LIFECYCLE_OPERATION, requestHash, result, now);
        publishWebhook(context.workspaceId(), context.workspaceSlug(), WebhookEventType.LINK_LIFECYCLE_CHANGED, "link-lifecycle:" + result.slug() + ":" + result.version(), result);
        invalidateWriteCaches(context.workspaceId(), linkSlug.value());
        return result;
    }

    @Override
    @Transactional
    public List<BulkLinkActionResult> bulkAction(
            WorkspaceAccessContext context,
            String action,
            List<String> slugs,
            List<String> tags,
            OffsetDateTime expiresAt,
            String idempotencyKey) {
        String normalizedAction = normalizeRequiredAction(action);
        List<String> normalizedTags = "update-tags".equals(normalizedAction) ? normalizeTags(tags) : tags;
        OffsetDateTime now = now();
        List<BulkLinkActionResult> results = new ArrayList<>();
        for (int index = 0; index < slugs.size(); index++) {
            String slug = slugs.get(index).trim();
            String itemIdempotencyKey = idempotencyKey + ":" + normalizedAction + ":" + slug;
            String bulkOperation = BULK_OPERATION_PREFIX + normalizedAction.toUpperCase(Locale.ROOT);
            String bulkRequestHash = fingerprintBulk(normalizedAction, slug, normalizedTags, expiresAt);
            LinkMutationResult replayed = findReplayIfPresent(context, itemIdempotencyKey, bulkOperation, bulkRequestHash);
            if (replayed != null) {
                results.add(new BulkLinkActionResult(slug, true, replayed.version(), null, null));
                continue;
            }
            try {
                LinkMutationResult result = switch (normalizedAction) {
                    case "archive" -> changeLifecycle(context, slug, "archive", null, currentVersion(context, slug), null);
                    case "suspend" -> changeLifecycle(context, slug, "suspend", null, currentVersion(context, slug), null);
                    case "delete" -> deleteLink(context, slug, currentVersion(context, slug), null);
                    case "update-tags" -> {
                        LinkDetails current = linkStore.findStoredDetailsBySlug(slug, context.workspaceId())
                                .orElseThrow(() -> new LinkNotFoundException(slug));
                        yield updateLink(
                                context,
                                slug,
                                current.originalUrl(),
                                current.expiresAt(),
                                current.title(),
                                normalizedTags,
                                current.version(),
                                null);
                    }
                    case "update-expiry" -> {
                        if (!expiresAt.isAfter(now)) {
                            throw new IllegalArgumentException("expiresAt must be in the future");
                        }
                        LinkDetails current = linkStore.findStoredDetailsBySlug(slug, context.workspaceId())
                                .orElseThrow(() -> new LinkNotFoundException(slug));
                        yield updateLink(
                                context,
                                slug,
                                current.originalUrl(),
                                expiresAt,
                                current.title(),
                                current.tags(),
                                current.version(),
                                null);
                    }
                    default -> throw new IllegalArgumentException("Unsupported bulk action: " + normalizedAction);
                };
                saveIdempotentResult(context, itemIdempotencyKey, bulkOperation, bulkRequestHash, result, now);
                results.add(new BulkLinkActionResult(slug, true, result.version(), null, null));
            } catch (LinkNotFoundException exception) {
                results.add(new BulkLinkActionResult(slug, false, null, "not-found", exception.getMessage()));
            } catch (LinkMutationConflictException exception) {
                results.add(new BulkLinkActionResult(slug, false, null, "conflict", exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                results.add(new BulkLinkActionResult(slug, false, null, "bad-request", exception.getMessage()));
            }
        }
        return List.copyOf(results);
    }

    @Override
    @Transactional(readOnly = true)
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        long generation = linkReadCache.getPublicRedirectGeneration(linkSlug.value());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findBySlug(linkSlug.value(), now())
                    .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        }
        return linkReadCache.getPublicRedirect(linkSlug.value(), generation)
                .or(() -> linkStore.findBySlug(linkSlug.value(), now())
                        .map(link -> {
                            linkReadCache.putPublicRedirect(linkSlug.value(), generation, link);
                            return link;
                        }))
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    @Transactional
    public void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress) {
        Long ownerId = linkStore.findOwnerIdBySlug(slug).orElse(null);
        analyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                UUID.randomUUID().toString(),
                slug,
                ownerId,
                now(),
                userAgent,
                referrer,
                remoteAddress));
    }

    @Override
    @Transactional(readOnly = true)
    public LinkDetails getLink(WorkspaceAccessContext context, String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        long generation = linkReadCache.getOwnerControlPlaneGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findDetailsBySlug(linkSlug.value(), now(), context.workspaceId())
                    .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        }
        return linkReadCache.getOwnerLinkDetails(context.workspaceId(), generation, linkSlug.value())
                .or(() -> linkStore.findDetailsBySlug(linkSlug.value(), now(), context.workspaceId())
                        .map(linkDetails -> {
                            linkReadCache.putOwnerLinkDetails(context.workspaceId(), generation, linkSlug.value(), linkDetails);
                            return linkDetails;
                        }))
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkDetails> listRecentLinks(
            WorkspaceAccessContext context,
            int limit,
            String query,
            LinkLifecycleState state,
            LinkAbuseStatus abuseStatus) {
        long generation = linkReadCache.getOwnerControlPlaneGeneration(context.workspaceId());
        if (abuseStatus != null || !linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findRecent(limit, now(), query, state, abuseStatus, context.workspaceId());
        }
        return linkReadCache.getOwnerRecentLinks(context.workspaceId(), generation, limit, query, state)
                .orElseGet(() -> {
                    List<LinkDetails> linkDetails = linkStore.findRecent(limit, now(), query, state, abuseStatus, context.workspaceId());
                    if (abuseStatus == null) {
                        linkReadCache.putOwnerRecentLinks(context.workspaceId(), generation, limit, query, state, linkDetails);
                    }
                    return linkDetails;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkSuggestion> suggestLinks(WorkspaceAccessContext context, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        long generation = linkReadCache.getOwnerControlPlaneGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findSuggestions(limit, now(), query, context.workspaceId());
        }
        return linkReadCache.getOwnerSuggestions(context.workspaceId(), generation, query, limit)
                .orElseGet(() -> {
                    List<LinkSuggestion> suggestions = linkStore.findSuggestions(limit, now(), query, context.workspaceId());
                    linkReadCache.putOwnerSuggestions(context.workspaceId(), generation, query, limit, suggestions);
                    return suggestions;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public LinkDiscoveryPage searchLinks(WorkspaceAccessContext context, LinkDiscoveryQuery query) {
        long generation = linkReadCache.getOwnerControlPlaneGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.searchDiscovery(now(), context.workspaceId(), query);
        }
        return linkReadCache.getOwnerDiscoveryPage(context.workspaceId(), generation, query)
                .orElseGet(() -> {
                    LinkDiscoveryPage discoveryPage = linkStore.searchDiscovery(now(), context.workspaceId(), query);
                    linkReadCache.putOwnerDiscoveryPage(context.workspaceId(), generation, query, discoveryPage);
                    return discoveryPage;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveLinks(WorkspaceAccessContext context) {
        return linkStore.countActiveLinksByOwner(context.workspaceId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkActivityEvent> getRecentActivity(WorkspaceAccessContext context, int limit) {
        validateAnalyticsLimit(limit);
        long generation = linkReadCache.getOwnerAnalyticsGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findRecentActivity(limit, context.workspaceId());
        }
        return linkReadCache.getOwnerRecentActivity(context.workspaceId(), generation, limit)
                .orElseGet(() -> {
                    List<LinkActivityEvent> activityEvents = linkStore.findRecentActivity(limit, context.workspaceId());
                    linkReadCache.putOwnerRecentActivity(context.workspaceId(), generation, limit, activityEvents);
                    return activityEvents;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkActivityEvent> getRecentActivity(WorkspaceAccessContext context, int limit, String tag, String lifecycle) {
        validateAnalyticsLimit(limit);
        if (isBlank(tag) && isBlank(lifecycle)) {
            return getRecentActivity(context, limit);
        }
        return linkStore.findRecentActivity(limit, normalizeTag(tag), parseLifecycleFilter(lifecycle), now(), context.workspaceId());
    }

    @Override
    @Transactional(readOnly = true)
    public LinkTrafficSummary getTrafficSummary(WorkspaceAccessContext context, String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        LocalDate startDate = now.toLocalDate().minusDays(6);
        long generation = linkReadCache.getOwnerAnalyticsGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return buildTrafficSummary(context.workspaceId(), generation, linkSlug, now, startDate);
        }

        return linkReadCache.getOwnerTrafficSummary(context.workspaceId(), generation, linkSlug.value())
                .orElseGet(() -> buildTrafficSummary(context.workspaceId(), generation, linkSlug, now, startDate));
    }

    private LinkTrafficSummary buildTrafficSummary(
            long ownerId,
            long generation,
            LinkSlug linkSlug,
            OffsetDateTime now,
            LocalDate startDate) {
        LinkTrafficSummaryTotals totals = linkStore.findTrafficSummaryTotals(
                        linkSlug.value(),
                        now.minusHours(24),
                        startDate,
                        ownerId)
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));

        LinkTrafficSummary summary = new LinkTrafficSummary(
                totals.slug(),
                totals.originalUrl(),
                totals.totalClicks(),
                totals.clicksLast24Hours(),
                totals.clicksLast7Days(),
                fillDailyBuckets(startDate, linkStore.findRecentDailyClickBuckets(linkSlug.value(), startDate, ownerId)));
        if (linkReadCache.isCacheGenerationAvailable(generation)) {
            linkReadCache.putOwnerTrafficSummary(ownerId, generation, linkSlug.value(), summary);
        }
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsSummaryView getTrafficSummary(WorkspaceAccessContext context, String slug, AnalyticsRange range) {
        LinkTrafficSummary summary = getTrafficSummary(context, slug);
        AnalyticsFreshness freshness = getAnalyticsFreshness(context, slug);
        if (range == null) {
            return new AnalyticsSummaryView(summary, null, null, null, freshness, null);
        }
        long currentWindowClicks = linkStore.countClicksForSlugInRange(slug, range.start(), range.end(), context.workspaceId());
        AnalyticsComparison comparison = range.comparePrevious()
                ? AnalyticsComparison.of(
                        range,
                        currentWindowClicks,
                        linkStore.countClicksForSlugInRange(slug, range.previousStart(), range.previousEnd(), context.workspaceId()))
                : null;
        return new AnalyticsSummaryView(
                summary,
                range.start(),
                range.end(),
                currentWindowClicks,
                freshness,
                comparison);
    }

    @Override
    @Transactional(readOnly = true)
    public LinkTrafficSeriesView getTrafficSeries(
            WorkspaceAccessContext context,
            String slug,
            AnalyticsRange range,
            String granularity) {
        LinkSlug linkSlug = new LinkSlug(slug);
        String normalizedGranularity = range.validateGranularity(granularity);
        linkStore.findTrafficSummaryTotals(
                        linkSlug.value(),
                        now().minusHours(24),
                        now().toLocalDate().minusDays(6),
                        context.workspaceId())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));

        List<LinkTrafficSeriesBucket> buckets = zeroFillBuckets(
                range,
                normalizedGranularity,
                linkStore.findTrafficSeries(linkSlug.value(), range.start(), range.end(), normalizedGranularity, context.workspaceId()));
        long currentWindowClicks = buckets.stream().mapToLong(LinkTrafficSeriesBucket::clickTotal).sum();
        AnalyticsComparison comparison = range.comparePrevious()
                ? AnalyticsComparison.of(
                        range,
                        currentWindowClicks,
                        linkStore.countClicksForSlugInRange(linkSlug.value(), range.previousStart(), range.previousEnd(), context.workspaceId()))
                : null;
        return new LinkTrafficSeriesView(
                linkSlug.value(),
                range.start(),
                range.end(),
                normalizedGranularity,
                buckets,
                getAnalyticsFreshness(context, linkSlug.value()),
                comparison);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopLinkTraffic> getTopLinks(WorkspaceAccessContext context, LinkTrafficWindow window) {
        long generation = linkReadCache.getOwnerAnalyticsGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findTopLinks(window, now(), context.workspaceId());
        }
        return linkReadCache.getOwnerTopLinks(context.workspaceId(), generation, window)
                .orElseGet(() -> {
                    List<TopLinkTraffic> topLinks = linkStore.findTopLinks(window, now(), context.workspaceId());
                    linkReadCache.putOwnerTopLinks(context.workspaceId(), generation, window, topLinks);
                    return topLinks;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopLinkTraffic> getTopLinks(
            WorkspaceAccessContext context,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit) {
        validateAnalyticsLimit(limit);
        if (range == null && isBlank(tag) && isBlank(lifecycle)) {
            return getTopLinks(context, window).stream().limit(limit).toList();
        }
        LinkLifecycleState lifecycleFilter = parseLifecycleFilter(lifecycle);
        String normalizedTag = normalizeTag(tag);
        OffsetDateTime asOf = now();
        if (range != null) {
            return linkStore.findTopLinks(
                    range.start(),
                    range.end(),
                    limit,
                    normalizedTag,
                    lifecycleFilter,
                    asOf,
                    context.workspaceId());
        }
        return linkStore.findTopLinks(window, asOf, limit, normalizedTag, lifecycleFilter, context.workspaceId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendingLink> getTrendingLinks(WorkspaceAccessContext context, LinkTrafficWindow window, int limit) {
        validateAnalyticsLimit(limit);
        long generation = linkReadCache.getOwnerAnalyticsGeneration(context.workspaceId());
        if (!linkReadCache.isCacheGenerationAvailable(generation)) {
            return linkStore.findTrendingLinks(window, now(), limit, context.workspaceId());
        }
        return linkReadCache.getOwnerTrendingLinks(context.workspaceId(), generation, window, limit)
                .orElseGet(() -> {
                    List<TrendingLink> trendingLinks = linkStore.findTrendingLinks(window, now(), limit, context.workspaceId());
                    linkReadCache.putOwnerTrendingLinks(context.workspaceId(), generation, window, limit, trendingLinks);
                    return trendingLinks;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendingLink> getTrendingLinks(
            WorkspaceAccessContext context,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit) {
        validateAnalyticsLimit(limit);
        if (range == null && isBlank(tag) && isBlank(lifecycle)) {
            return getTrendingLinks(context, window, limit);
        }
        LinkLifecycleState lifecycleFilter = parseLifecycleFilter(lifecycle);
        String normalizedTag = normalizeTag(tag);
        OffsetDateTime asOf = now();
        if (range != null) {
            return linkStore.findTrendingLinks(
                    range.start(),
                    range.end(),
                    limit,
                    normalizedTag,
                    lifecycleFilter,
                    asOf,
                    context.workspaceId());
        }
        return linkStore.findTrendingLinks(window, asOf, limit, normalizedTag, lifecycleFilter, context.workspaceId());
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsFreshness getAnalyticsFreshness(WorkspaceAccessContext context) {
        OffsetDateTime asOf = now();
        return toFreshness(
                asOf,
                linkStore.findLatestMaterializedClickAt(context.workspaceId()).orElse(null),
                linkStore.findLatestMaterializedActivityAt(context.workspaceId()).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsFreshness getAnalyticsFreshness(WorkspaceAccessContext context, String slug) {
        OffsetDateTime asOf = now();
        return toFreshness(
                asOf,
                linkStore.findLatestMaterializedClickAt(slug, context.workspaceId()).orElse(null),
                linkStore.findLatestMaterializedActivityAt(slug, context.workspaceId()).orElse(null));
    }

    private void rejectReservedSlug(LinkSlug slug) {
        String normalizedSlug = slug.value().toLowerCase(Locale.ROOT);

        if (RESERVED_TOP_LEVEL_SLUGS.contains(normalizedSlug)) {
            throw new ReservedLinkSlugException(slug.value());
        }
    }

    private void rejectSelfTargetUrl(OriginalUrl originalUrl) {
        URI targetUri = URI.create(originalUrl.value());

        if (isSameOrigin(publicBaseUri, targetUri)) {
            throw new SelfTargetLinkException(originalUrl.value());
        }
    }

    private boolean isSameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }

        return 80;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String normalizedTitle = title.trim();
        return normalizedTitle.isEmpty() ? null : normalizedTitle;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }

        return tags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private String extractHostname(String originalUrl) {
        String host = URI.create(originalUrl).getHost();
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private LinkLifecycleEvent toLifecycleEvent(
            LinkLifecycleEventType type,
            long ownerId,
            long workspaceId,
            LinkDetails linkDetails,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
                ownerId,
                workspaceId,
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.title(),
                linkDetails.tags(),
                linkDetails.hostname(),
                linkDetails.expiresAt(),
                LinkLifecycleState.ACTIVE,
                linkDetails.version(),
                occurredAt);
    }

    private LinkLifecycleEvent toLifecycleEvent(
            LinkLifecycleEventType type,
            long ownerId,
            long workspaceId,
            LinkDetails linkDetails,
            LinkLifecycleState lifecycleState,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
                ownerId,
                workspaceId,
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.title(),
                linkDetails.tags(),
                linkDetails.hostname(),
                linkDetails.expiresAt(),
                lifecycleState,
                linkDetails.version(),
                occurredAt);
    }

    private LinkLifecycleEvent toLifecycleEvent(
            LinkLifecycleEventType type,
            long ownerId,
            long workspaceId,
            LinkMutationResult result,
            LinkLifecycleState lifecycleState,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
                ownerId,
                workspaceId,
                result.slug(),
                result.originalUrl(),
                result.title(),
                result.tags(),
                result.hostname(),
                result.expiresAt(),
                lifecycleState,
                result.version(),
                occurredAt);
    }

    private LinkLifecycleEventType determineUpdateEventType(LinkDetails beforeUpdate, LinkDetails afterUpdate) {
        boolean expiresAtChanged = !java.util.Objects.equals(beforeUpdate.expiresAt(), afterUpdate.expiresAt());
        boolean nonExpirationFieldsChanged = !java.util.Objects.equals(beforeUpdate.originalUrl(), afterUpdate.originalUrl())
                || !java.util.Objects.equals(beforeUpdate.title(), afterUpdate.title())
                || !java.util.Objects.equals(beforeUpdate.tags(), afterUpdate.tags())
                || !java.util.Objects.equals(beforeUpdate.hostname(), afterUpdate.hostname());
        return expiresAtChanged && !nonExpirationFieldsChanged
                ? LinkLifecycleEventType.EXPIRATION_UPDATED
                : LinkLifecycleEventType.UPDATED;
    }

    private LinkLifecycleEventType determineLifecycleEventType(LifecycleAction action) {
        return switch (action) {
            case SUSPEND -> LinkLifecycleEventType.SUSPENDED;
            case RESUME -> LinkLifecycleEventType.RESUMED;
            case ARCHIVE -> LinkLifecycleEventType.ARCHIVED;
            case UNARCHIVE -> LinkLifecycleEventType.UNARCHIVED;
            case EXPIRE_NOW -> LinkLifecycleEventType.EXPIRED;
            case EXTEND_EXPIRY -> LinkLifecycleEventType.EXPIRATION_UPDATED;
            case RESTORE -> LinkLifecycleEventType.RESTORED;
        };
    }

    private LifecycleAction parseLifecycleAction(String action) {
        String normalized = normalizeRequiredAction(action);
        return switch (normalized) {
            case "suspend" -> LifecycleAction.SUSPEND;
            case "resume" -> LifecycleAction.RESUME;
            case "archive" -> LifecycleAction.ARCHIVE;
            case "unarchive" -> LifecycleAction.UNARCHIVE;
            case "restore" -> LifecycleAction.RESTORE;
            case "expire-now" -> LifecycleAction.EXPIRE_NOW;
            case "extend-expiry" -> LifecycleAction.EXTEND_EXPIRY;
            default -> throw new IllegalArgumentException(
                    "Lifecycle action must be one of: suspend, resume, archive, unarchive, restore, expire-now, extend-expiry");
        };
    }

    private String normalizeRequiredAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Lifecycle action is required");
        }
        return action.trim().toLowerCase(Locale.ROOT);
    }

    private void validateLifecycleAction(LinkLifecycleState currentState, LifecycleAction action) {
        boolean valid = switch (currentState) {
            case ACTIVE -> action == LifecycleAction.SUSPEND
                    || action == LifecycleAction.ARCHIVE
                    || action == LifecycleAction.EXPIRE_NOW
                    || action == LifecycleAction.EXTEND_EXPIRY;
            case SUSPENDED -> action == LifecycleAction.RESUME
                    || action == LifecycleAction.ARCHIVE
                    || action == LifecycleAction.EXPIRE_NOW
                    || action == LifecycleAction.EXTEND_EXPIRY;
            case ARCHIVED -> action == LifecycleAction.UNARCHIVE;
            case EXPIRED -> action == LifecycleAction.EXTEND_EXPIRY || action == LifecycleAction.ARCHIVE;
            case ALL -> action == LifecycleAction.RESTORE;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Lifecycle action " + action.name().toLowerCase(Locale.ROOT).replace('_', '-') + " is not allowed from "
                            + currentState.name().toLowerCase(Locale.ROOT));
        }
    }

    private LinkLifecycleState resolveCurrentState(
            LinkDetails current,
            LinkStore.DeletedLinkSnapshot deletedSnapshot,
            long ownerId,
            String slug,
            OffsetDateTime now) {
        if (current != null) {
            return resolveCurrentState(current, ownerId, now);
        }
        if (deletedSnapshot != null) {
            return LinkLifecycleState.ALL;
        }
        throw new LinkNotFoundException(slug);
    }

    private LinkLifecycleState resolveCurrentState(LinkDetails current, long ownerId, OffsetDateTime now) {
        LinkLifecycleState storedState = linkStore.findLifecycleStateBySlug(current.slug(), ownerId)
                .orElse(LinkLifecycleState.ACTIVE);
        if (storedState == LinkLifecycleState.ARCHIVED) {
            return LinkLifecycleState.ARCHIVED;
        }
        if (storedState == LinkLifecycleState.SUSPENDED) {
            return LinkLifecycleState.SUSPENDED;
        }
        if (current.expiresAt() != null && !current.expiresAt().isAfter(now)) {
            return LinkLifecycleState.EXPIRED;
        }
        return LinkLifecycleState.ACTIVE;
    }

    private LinkLifecycleState persistedStateFor(LinkLifecycleState logicalState) {
        return switch (logicalState) {
            case ACTIVE, EXPIRED, ALL -> LinkLifecycleState.ACTIVE;
            case SUSPENDED -> LinkLifecycleState.SUSPENDED;
            case ARCHIVED -> LinkLifecycleState.ARCHIVED;
        };
    }

    private OffsetDateTime resolveNextExpiry(
            LinkDetails current,
            LifecycleAction action,
            OffsetDateTime requestedExpiry,
            OffsetDateTime now) {
        return switch (action) {
            case EXPIRE_NOW -> now;
            case EXTEND_EXPIRY -> validateExtension(current.expiresAt(), requestedExpiry, now);
            case SUSPEND, RESUME, ARCHIVE, UNARCHIVE -> current.expiresAt();
            case RESTORE -> requestedExpiry;
        };
    }

    private OffsetDateTime validateExtension(OffsetDateTime currentExpiry, OffsetDateTime requestedExpiry, OffsetDateTime now) {
        if (requestedExpiry == null) {
            throw new IllegalArgumentException("expiresAt is required for action extend-expiry");
        }
        if (!requestedExpiry.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        if (currentExpiry != null && !requestedExpiry.isAfter(currentExpiry)) {
            throw new IllegalArgumentException("expiresAt must extend the current expiry");
        }
        return requestedExpiry;
    }

    private LinkLifecycleState resolveNextState(
            LinkLifecycleState currentState,
            LifecycleAction action,
            OffsetDateTime nextExpiresAt,
            OffsetDateTime now) {
        return switch (action) {
            case SUSPEND -> LinkLifecycleState.SUSPENDED;
            case RESUME, UNARCHIVE -> nextExpiresAt != null && !nextExpiresAt.isAfter(now)
                    ? LinkLifecycleState.EXPIRED
                    : LinkLifecycleState.ACTIVE;
            case ARCHIVE -> LinkLifecycleState.ARCHIVED;
            case EXPIRE_NOW -> LinkLifecycleState.EXPIRED;
            case EXTEND_EXPIRY -> currentState == LinkLifecycleState.SUSPENDED
                    ? LinkLifecycleState.SUSPENDED
                    : LinkLifecycleState.ACTIVE;
            case RESTORE -> LinkLifecycleState.ACTIVE;
        };
    }

    private LinkLifecycleState restoreTargetState(LinkStore.DeletedLinkSnapshot deletedSnapshot, OffsetDateTime now) {
        if (deletedSnapshot.lifecycleState() == LinkLifecycleState.SUSPENDED) {
            return LinkLifecycleState.SUSPENDED;
        }
        if (deletedSnapshot.expiresAt() != null && !deletedSnapshot.expiresAt().isAfter(now)) {
            return LinkLifecycleState.EXPIRED;
        }
        return deletedSnapshot.lifecycleState() == LinkLifecycleState.ARCHIVED
                ? LinkLifecycleState.ACTIVE
                : LinkLifecycleState.ACTIVE;
    }

    private long currentVersion(WorkspaceAccessContext context, String slug) {
        return linkStore.findStoredDetailsBySlug(slug, context.workspaceId())
                .map(LinkDetails::version)
                .orElseThrow(() -> new LinkNotFoundException(slug));
    }

    private List<DailyClickBucket> fillDailyBuckets(LocalDate startDate, List<DailyClickBucket> buckets) {
        Map<LocalDate, Long> clickTotalsByDay = new HashMap<>();
        for (DailyClickBucket bucket : buckets) {
            clickTotalsByDay.put(bucket.day(), bucket.clickTotal());
        }

        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(startDate::plusDays)
                .map(day -> new DailyClickBucket(day, clickTotalsByDay.getOrDefault(day, 0L)))
                .toList();
    }

    private List<LinkTrafficSeriesBucket> zeroFillBuckets(
            AnalyticsRange range,
            String granularity,
            List<LinkTrafficSeriesBucket> buckets) {
        Map<OffsetDateTime, Long> totalsByBucketStart = new HashMap<>();
        for (LinkTrafficSeriesBucket bucket : buckets) {
            totalsByBucketStart.put(bucket.bucketStart(), bucket.clickTotal());
        }
        List<LinkTrafficSeriesBucket> filledBuckets = new ArrayList<>();
        OffsetDateTime bucketStart = range.start();
        while (bucketStart.isBefore(range.end())) {
            OffsetDateTime bucketEnd = nextBucketStart(bucketStart, granularity);
            filledBuckets.add(new LinkTrafficSeriesBucket(
                    bucketStart,
                    bucketEnd.isAfter(range.end()) ? range.end() : bucketEnd,
                    totalsByBucketStart.getOrDefault(bucketStart, 0L)));
            bucketStart = bucketEnd;
        }
        return List.copyOf(filledBuckets);
    }

    private OffsetDateTime nextBucketStart(OffsetDateTime bucketStart, String granularity) {
        return switch (granularity) {
            case "hour" -> bucketStart.plusHours(1);
            case "day" -> bucketStart.plusDays(1);
            default -> throw new IllegalArgumentException("granularity must be one of: hour, day");
        };
    }

    private AnalyticsFreshness toFreshness(
            OffsetDateTime asOf,
            OffsetDateTime latestMaterializedClickAt,
            OffsetDateTime latestMaterializedActivityAt) {
        return new AnalyticsFreshness(
                asOf,
                latestMaterializedClickAt,
                latestMaterializedActivityAt,
                lagSeconds(asOf, latestMaterializedClickAt),
                lagSeconds(asOf, latestMaterializedActivityAt));
    }

    private Long lagSeconds(OffsetDateTime asOf, OffsetDateTime latestTimestamp) {
        if (latestTimestamp == null) {
            return null;
        }
        return java.time.Duration.between(latestTimestamp, asOf).getSeconds();
    }

    private void validateAnalyticsLimit(int limit) {
        if (limit < 1 || limit > MAX_ANALYTICS_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_ANALYTICS_LIMIT);
        }
    }

    private LinkLifecycleState parseLifecycleFilter(String lifecycle) {
        if (isBlank(lifecycle)) {
            return null;
        }
        return switch (lifecycle.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> LinkLifecycleState.ACTIVE;
            case "suspended" -> LinkLifecycleState.SUSPENDED;
            case "archived" -> LinkLifecycleState.ARCHIVED;
            case "expired" -> LinkLifecycleState.EXPIRED;
            case "all" -> LinkLifecycleState.ALL;
            default -> throw new IllegalArgumentException("Lifecycle must be one of: active, suspended, archived, expired, all");
        };
    }

    private String normalizeTag(String tag) {
        if (isBlank(tag)) {
            return null;
        }
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LinkMutationResult findReplayIfPresent(WorkspaceAccessContext context, String idempotencyKey, String operation, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return linkMutationIdempotencyStore.findByKey(context.ownerId(), workspaceScopedIdempotencyKey(context, idempotencyKey))
                .map(record -> {
                    if (!record.operation().equals(operation) || !record.requestHash().equals(requestHash)) {
                        throw new LinkMutationConflictException("Idempotency key cannot be reused for a different link mutation request");
                    }
                    return record.result();
                })
                .orElse(null);
    }

    private void saveIdempotentResult(
            WorkspaceAccessContext context,
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        linkMutationIdempotencyStore.saveResult(
                context.ownerId(),
                workspaceScopedIdempotencyKey(context, idempotencyKey),
                operation,
                requestHash,
                result,
                createdAt);
    }

    private void enforceCreateQuota(WorkspaceAccessContext context, OffsetDateTime occurredAt) {
        long currentUsage = linkStore.countActiveLinksByOwner(context.workspaceId());
        try {
            if (workspaceEntitlementService != null) {
                workspaceEntitlementService.enforceActiveLinksQuota(context.workspaceId(), currentUsage);
            } else if (currentUsage >= context.plan().activeLinkLimit()) {
                throw new WorkspaceQuotaExceededException(
                        com.linkplatform.api.owner.application.WorkspaceUsageMetric.ACTIVE_LINKS,
                        currentUsage,
                        context.plan().activeLinkLimit(),
                        "Active link quota exceeded for owner " + context.ownerKey() + " on plan " + context.plan().name());
            }
        } catch (WorkspaceQuotaExceededException exception) {
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_QUOTA_EXCEEDED,
                    context.ownerId(),
                    context.workspaceId(),
                    null,
                    "POST",
                    "/api/v1/links",
                    null,
                    "Active link quota exceeded",
                    occurredAt);
            throw exception;
        }
    }

    private void recordActiveLinksSnapshot(long workspaceId, String slug, OffsetDateTime occurredAt, String source) {
        if (workspaceEntitlementService == null) {
            return;
        }
        workspaceEntitlementService.recordActiveLinksSnapshot(
                workspaceId,
                linkStore.countActiveLinksByOwner(workspaceId),
                source,
                slug,
                occurredAt);
    }

    private void publishWebhook(long workspaceId, String workspaceSlug, WebhookEventType eventType, String eventId, Object payload) {
        if (webhookEventPublisher != null) {
            webhookEventPublisher.publish(workspaceId, eventType, eventId, payload);
        }
    }

    private void rejectUnsafeTarget(WorkspaceAccessContext context, String slug, TargetRiskAssessment targetRiskAssessment) {
        if (linkAbuseReviewService == null) {
            throw new UnsafeLinkTargetException(targetRiskAssessment.summary());
        }
        linkAbuseReviewService.rejectUnsafeTarget(context, slug, targetRiskAssessment);
    }

    private void flagTargetForReview(WorkspaceAccessContext context, String slug, TargetRiskAssessment targetRiskAssessment) {
        if (linkAbuseReviewService != null) {
            linkAbuseReviewService.flagTargetForReview(context, slug, targetRiskAssessment);
        }
    }

    private String workspaceScopedIdempotencyKey(WorkspaceAccessContext context, String idempotencyKey) {
        return context.workspaceId() + ":" + idempotencyKey;
    }

    private String fingerprintCreate(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags) {
        return sha256(CREATE_OPERATION + "|" + slug + "|" + originalUrl + "|" + expiresAt + "|" + title + "|" + String.join(",", tags));
    }

    private String fingerprintUpdate(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion) {
        return sha256(UPDATE_OPERATION + "|" + slug + "|" + originalUrl + "|" + expiresAt + "|" + title + "|"
                + String.join(",", tags) + "|" + expectedVersion);
    }

    private String fingerprintDelete(String slug, long expectedVersion) {
        return sha256(DELETE_OPERATION + "|" + slug + "|" + expectedVersion);
    }

    private String fingerprintLifecycle(String slug, LifecycleAction action, OffsetDateTime expiresAt, long expectedVersion) {
        return sha256(LIFECYCLE_OPERATION + "|" + slug + "|" + action.name() + "|" + expiresAt + "|" + expectedVersion);
    }

    private String fingerprintBulk(String action, String slug, List<String> tags, OffsetDateTime expiresAt) {
        String normalizedTags = tags == null ? "" : String.join(",", tags);
        return sha256(BULK_OPERATION_PREFIX + action + "|" + slug + "|" + normalizedTags + "|" + expiresAt);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private void invalidateWriteCaches(long ownerId, String slug) {
        linkReadCache.invalidatePublicRedirect(slug);
        linkReadCache.invalidateOwnerControlPlane(ownerId);
        linkReadCache.invalidateOwnerAnalytics(ownerId);
    }

    private void applyLifecycleReadModels(LinkLifecycleEvent lifecycleEvent) {
        linkStore.recordActivityIfAbsent(lifecycleEvent.eventId(), toActivityEvent(lifecycleEvent));
        linkStore.projectCatalogEvent(lifecycleEvent);
        linkStore.projectDiscoveryEvent(lifecycleEvent);
    }

    private LinkActivityEvent toActivityEvent(LinkLifecycleEvent lifecycleEvent) {
        LinkActivityType activityType = switch (lifecycleEvent.eventType()) {
            case CREATED -> LinkActivityType.CREATED;
            case UPDATED, RESTORED, EXPIRED, EXPIRATION_UPDATED, SUSPENDED, RESUMED, ARCHIVED, UNARCHIVED -> LinkActivityType.UPDATED;
            case DELETED -> LinkActivityType.DELETED;
        };
        return new LinkActivityEvent(
                lifecycleEvent.ownerId(),
                lifecycleEvent.workspaceId(),
                activityType,
                lifecycleEvent.slug(),
                lifecycleEvent.originalUrl(),
                lifecycleEvent.title(),
                lifecycleEvent.tags(),
                lifecycleEvent.hostname(),
                lifecycleEvent.expiresAt(),
                lifecycleEvent.occurredAt());
    }

    private enum LifecycleAction {
        SUSPEND,
        RESUME,
        ARCHIVE,
        UNARCHIVE,
        RESTORE,
        EXPIRE_NOW,
        EXTEND_EXPIRY
    }
}
