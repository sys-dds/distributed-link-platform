package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
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
    private final URI publicBaseUri;
    private final Clock clock;

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            AnalyticsOutboxStore analyticsOutboxStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkMutationIdempotencyStore linkMutationIdempotencyStore,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkMutationIdempotencyStore = linkMutationIdempotencyStore;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public LinkMutationResult createLink(CreateLinkCommand command, String idempotencyKey) {
        OffsetDateTime now = now();
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());
        rejectSelfTargetUrl(link.originalUrl());
        String normalizedTitle = normalizeTitle(command.title());
        List<String> normalizedTags = normalizeTags(command.tags());
        String hostname = extractHostname(link.originalUrl().value());
        String requestHash = fingerprintCreate(command.slug(), command.originalUrl(), command.expiresAt(), normalizedTitle, normalizedTags);
        LinkMutationResult replayed = findReplayIfPresent(idempotencyKey, CREATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        if (!linkStore.save(link, command.expiresAt(), normalizedTitle, normalizedTags, hostname, 1L)) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }
        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(link.slug().value())
                .orElseThrow(() -> new LinkNotFoundException(link.slug().value()));
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                LinkLifecycleEventType.CREATED,
                storedDetails.slug(),
                storedDetails.originalUrl(),
                storedDetails.title(),
                storedDetails.tags(),
                storedDetails.hostname(),
                storedDetails.expiresAt(),
                storedDetails.version(),
                now));
        LinkMutationResult result = LinkMutationResult.fromDetails(storedDetails);
        saveIdempotentResult(idempotencyKey, CREATE_OPERATION, requestHash, result, now);
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult updateLink(
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
        LinkMutationResult replayed = findReplayIfPresent(idempotencyKey, UPDATE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails beforeUpdate = linkStore.findStoredDetailsBySlug(linkSlug.value())
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
                nextVersion)) {
            throw new LinkMutationConflictException("Link version conflict for slug: " + linkSlug.value());
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        linkLifecycleOutboxStore.saveLinkLifecycleEvent(toLifecycleEvent(
                determineUpdateEventType(beforeUpdate, storedDetails),
                storedDetails,
                now));
        LinkDetails visibleDetails = linkStore.findDetailsBySlug(linkSlug.value(), now)
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
        LinkMutationResult result = LinkMutationResult.fromDetails(visibleDetails);
        saveIdempotentResult(idempotencyKey, UPDATE_OPERATION, requestHash, result, now);
        return result;
    }

    @Override
    @Transactional
    public LinkMutationResult deleteLink(String slug, long expectedVersion, String idempotencyKey) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        String requestHash = fingerprintDelete(linkSlug.value(), expectedVersion);
        LinkMutationResult replayed = findReplayIfPresent(idempotencyKey, DELETE_OPERATION, requestHash);
        if (replayed != null) {
            return replayed;
        }

        LinkDetails storedDetails = linkStore.findStoredDetailsBySlug(linkSlug.value())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));

        if (!linkStore.deleteBySlug(linkSlug.value(), expectedVersion)) {
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
                result,
                now));
        saveIdempotentResult(idempotencyKey, DELETE_OPERATION, requestHash, result, now);
        return result;
    }

    @Override
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findBySlug(linkSlug.value(), now())
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
    public LinkDetails getLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findDetailsBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public List<LinkDetails> listRecentLinks(int limit, String query, LinkLifecycleState state) {
        return linkStore.findRecent(limit, now(), query, state);
    }

    @Override
    public List<LinkSuggestion> suggestLinks(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return linkStore.findSuggestions(limit, now(), query);
    }

    @Override
    public List<LinkActivityEvent> getRecentActivity(int limit) {
        return linkStore.findRecentActivity(limit);
    }

    @Override
    public LinkTrafficSummary getTrafficSummary(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OffsetDateTime now = now();
        LocalDate startDate = now.toLocalDate().minusDays(6);

        LinkTrafficSummaryTotals totals = linkStore.findTrafficSummaryTotals(
                        linkSlug.value(),
                        now.minusHours(24),
                        startDate)
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));

        return new LinkTrafficSummary(
                totals.slug(),
                totals.originalUrl(),
                totals.totalClicks(),
                totals.clicksLast24Hours(),
                totals.clicksLast7Days(),
                fillDailyBuckets(startDate, linkStore.findRecentDailyClickBuckets(linkSlug.value(), startDate)));
    }

    @Override
    public List<TopLinkTraffic> getTopLinks(LinkTrafficWindow window) {
        return linkStore.findTopLinks(window, now());
    }

    @Override
    public List<TrendingLink> getTrendingLinks(LinkTrafficWindow window, int limit) {
        return linkStore.findTrendingLinks(window, now(), limit);
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
            LinkDetails linkDetails,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
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
            LinkMutationResult result,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                UUID.randomUUID().toString(),
                type,
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

    private LinkMutationResult findReplayIfPresent(String idempotencyKey, String operation, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return linkMutationIdempotencyStore.findByKey(idempotencyKey)
                .map(record -> {
                    if (!record.operation().equals(operation) || !record.requestHash().equals(requestHash)) {
                        throw new LinkMutationConflictException("Idempotency key cannot be reused for a different link mutation request");
                    }
                    return record.result();
                })
                .orElse(null);
    }

    private void saveIdempotentResult(
            String idempotencyKey,
            String operation,
            String requestHash,
            LinkMutationResult result,
            OffsetDateTime createdAt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        linkMutationIdempotencyStore.saveResult(idempotencyKey, operation, requestHash, result, createdAt);
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
}
