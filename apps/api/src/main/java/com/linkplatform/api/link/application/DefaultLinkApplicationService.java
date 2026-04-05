package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerQuotaExceededException;
import com.linkplatform.api.owner.application.OwnerStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private static final Set<String> RESERVED_TOP_LEVEL_SLUGS = Set.of("api", "actuator", "error");
    private static final String CREATE_OPERATION = "CREATE";
    private static final String UPDATE_OPERATION = "UPDATE";
    private static final String DELETE_OPERATION = "DELETE";

    private final LinkStore linkStore;
    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final LinkMutationIdempotencyStore linkMutationIdempotencyStore;
    private final OwnerStore ownerStore;
    private final SecurityEventStore securityEventStore;
    private final LinkReadCache linkReadCache;
    private final URI publicBaseUri;
    private final Clock clock;

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkMutationIdempotencyStore linkMutationIdempotencyStore,
            OwnerStore ownerStore,
            SecurityEventStore securityEventStore,
            LinkReadCache linkReadCache,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkMutationIdempotencyStore = linkMutationIdempotencyStore;
        this.ownerStore = ownerStore;
        this.securityEventStore = securityEventStore;
        this.linkReadCache = linkReadCache;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public LinkMutationResult createLink(AuthenticatedOwner owner, CreateLinkCommand command, String idempotencyKey) {
        OffsetDateTime now = now();
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());
        rejectSelfTargetUrl(link.originalUrl());
        String normalizedTitle = normalizeTitle(command.title());
        List<String> normalizedTags = normalizeTags(command.tags());
        String hostname = extractHostname(link.originalUrl().value());
        String requestHash = fingerprintCreate(command.slug(), command.originalUrl(), command.expiresAt(), normalizedTitle, normalizedTags);
        LinkMutationResult replayed = findReplayIfPresent(owner.id(), idempotencyKey, CREATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        ownerStore.lockById(owner.id());
        enforceCreateQuota(owner, now);

        if (!linkStore.save(link, command.expiresAt(), normalizedTitle, normalizedTags, hostname, 1L, owner.id())) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }
        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(link.slug().value(), owner.id())
                .orElseThrow(() -> new LinkNotFoundException(link.slug().value()));
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                LinkLifecycleEventType.CREATED,
                owner.id(),
                storedDetails.slug(),
                storedDetails.originalUrl(),
                storedDetails.title(),
                storedDetails.tags(),
                storedDetails.hostname(),
                storedDetails.expiresAt(),
                storedDetails.version(),
                now));
        LinkMutationResult result = LinkMutationResult.fromDetails(storedDetails);
        saveIdempotentResult(owner.id(), idempotencyKey, CREATE_OPERATION, requestHash, result, now);
        invalidateWriteCaches(owner.id(), link.slug().value());
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult updateLink(
            AuthenticatedOwner owner,
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
        LinkMutationResult replayed = findReplayIfPresent(owner.id(), idempotencyKey, UPDATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails beforeUpdate = linkStore.findStoredDetailsBySlug(linkSlug.value(), owner.id())
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
                owner.id())) {
            throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value(), owner.id())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(toLifecycleEvent(
                determineUpdateEventType(beforeUpdate, storedDetails),
                owner.id(),
                storedDetails,
                now));
        LinkDetails visibleDetails = linkStore.findDetailsBySlug(linkSlug.value(), now, owner.id())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        LinkMutationResult result = LinkMutationResult.fromDetails(visibleDetails);
        saveIdempotentResult(owner.id(), idempotencyKey, UPDATE_OPERATION, requestHash, result, now);
        invalidateWriteCaches(owner.id(), linkSlug.value());
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult deleteLink(AuthenticatedOwner owner, String slug, long expectedVersion, String idempotencyKey) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        String requestHash = fingerprintDelete(linkSlug.value(), expectedVersion);
        LinkMutationResult replayed = findReplayIfPresent(owner.id(), idempotencyKey, DELETE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value(), owner.id())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));

        if (!linkStore.deleteBySlug(linkSlug.value(), expectedVersion, owner.id())) {
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
                storedDetails.version() + 1,
                true);
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(toLifecycleEvent(
                LinkLifecycleEventType.DELETED,
                owner.id(),
                result,
                now));
        saveIdempotentResult(owner.id(), idempotencyKey, DELETE_OPERATION, requestHash, result, now);
        invalidateWriteCaches(owner.id(), linkSlug.value());
        return result;
    }

    @Override
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        return linkReadCache.getPublicRedirect(linkSlug.value())
                .or(() -> linkStore.findBySlug(linkSlug.value(), now())
                        .map(link -> {
                            linkReadCache.putPublicRedirect(linkSlug.value(), link);
                            return link;
                        }))
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    @Transactional
    public void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress) {
        analyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                UUID.randomUUID().toString(),
                slug,
                now(),
                userAgent,
                referrer,
                remoteAddress));
    }

    @Override
    public LinkDetails getLink(AuthenticatedOwner owner, String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        return linkReadCache.getOwnerLinkDetails(owner.id(), linkSlug.value())
                .or(() -> linkStore.findDetailsBySlug(linkSlug.value(), now(), owner.id())
                        .map(linkDetails -> {
                            linkReadCache.putOwnerLinkDetails(owner.id(), linkSlug.value(), linkDetails);
                            return linkDetails;
                        }))
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public List<LinkDetails> listRecentLinks(AuthenticatedOwner owner, int limit, String query, LinkLifecycleState state) {
        return linkReadCache.getOwnerRecentLinks(owner.id(), limit, query, state)
                .orElseGet(() -> {
                    List<LinkDetails> linkDetails = linkStore.findRecent(limit, now(), query, state, owner.id());
                    linkReadCache.putOwnerRecentLinks(owner.id(), limit, query, state, linkDetails);
                    return linkDetails;
                });
    }

    @Override
    public List<LinkSuggestion> suggestLinks(AuthenticatedOwner owner, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return linkReadCache.getOwnerSuggestions(owner.id(), query, limit)
                .orElseGet(() -> {
                    List<LinkSuggestion> suggestions = linkStore.findSuggestions(limit, now(), query, owner.id());
                    linkReadCache.putOwnerSuggestions(owner.id(), query, limit, suggestions);
                    return suggestions;
                });
    }

    @Override
    public long countActiveLinks(AuthenticatedOwner owner) {
        return linkStore.countActiveLinksByOwner(owner.id());
    }

    @Override
    public List<LinkActivityEvent> getRecentActivity(AuthenticatedOwner owner, int limit) {
        return linkReadCache.getOwnerRecentActivity(owner.id(), limit)
                .orElseGet(() -> {
                    List<LinkActivityEvent> activityEvents = linkStore.findRecentActivity(limit, owner.id());
                    linkReadCache.putOwnerRecentActivity(owner.id(), limit, activityEvents);
                    return activityEvents;
                });
    }

    @Override
    public LinkTrafficSummary getTrafficSummary(AuthenticatedOwner owner, String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        LocalDate startDate = now.toLocalDate().minusDays(6);

        return linkReadCache.getOwnerTrafficSummary(owner.id(), linkSlug.value())
                .orElseGet(() -> buildTrafficSummary(owner.id(), linkSlug, now, startDate));
    }

    private LinkTrafficSummary buildTrafficSummary(long ownerId, LinkSlug linkSlug, OffsetDateTime now, LocalDate startDate) {
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
        linkReadCache.putOwnerTrafficSummary(ownerId, linkSlug.value(), summary);
        return summary;
    }

    @Override
    public List<TopLinkTraffic> getTopLinks(AuthenticatedOwner owner, LinkTrafficWindow window) {
        return linkReadCache.getOwnerTopLinks(owner.id(), window)
                .orElseGet(() -> {
                    List<TopLinkTraffic> topLinks = linkStore.findTopLinks(window, now(), owner.id());
                    linkReadCache.putOwnerTopLinks(owner.id(), window, topLinks);
                    return topLinks;
                });
    }

    @Override
    public List<TrendingLink> getTrendingLinks(AuthenticatedOwner owner, LinkTrafficWindow window, int limit) {
        return linkReadCache.getOwnerTrendingLinks(owner.id(), window, limit)
                .orElseGet(() -> {
                    List<TrendingLink> trendingLinks = linkStore.findTrendingLinks(window, now(), limit, owner.id());
                    linkReadCache.putOwnerTrendingLinks(owner.id(), window, limit, trendingLinks);
                    return trendingLinks;
                });
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
            LinkDetails linkDetails,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
                ownerId,
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.title(),
                linkDetails.tags(),
                linkDetails.hostname(),
                linkDetails.expiresAt(),
                linkDetails.version(),
                occurredAt);
    }

    private LinkLifecycleEvent toLifecycleEvent(
            LinkLifecycleEventType type,
            long ownerId,
            LinkMutationResult result,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
                ownerId,
                result.slug(),
                result.originalUrl(),
                result.title(),
                result.tags(),
                result.hostname(),
                result.expiresAt(),
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

    private LinkMutationResult findReplayIfPresent(long ownerId, String idempotencyKey, String operation, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return linkMutationIdempotencyStore.findByKey(ownerId, idempotencyKey)
                .map(record -> {
                    if (!record.operation().equals(operation) || !record.requestHash().equals(requestHash)) {
                        throw new LinkMutationConflictException("Idempotency key cannot be reused for a different link mutation request");
                    }
                    return record.result();
                })
                .orElse(null);
    }

    private void saveIdempotentResult(
            long ownerId,
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        linkMutationIdempotencyStore.saveResult(ownerId, idempotencyKey, operation, requestHash, result, createdAt);
    }

    private void enforceCreateQuota(AuthenticatedOwner owner, OffsetDateTime occurredAt) {
        if (linkStore.countActiveLinksByOwner(owner.id()) >= owner.plan().activeLinkLimit()) {
            securityEventStore.record(
                    SecurityEventType.QUOTA_REJECTED,
                    owner.id(),
                    null,
                    "POST",
                    "/api/v1/links",
                    null,
                    "Active link quota exceeded",
                    occurredAt);
            throw new OwnerQuotaExceededException(
                    "Active link quota exceeded for owner " + owner.ownerKey() + " on plan " + owner.plan().name());
        }
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
    }
}
