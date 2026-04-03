package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private static final Set<String> RESERVED_TOP_LEVEL_SLUGS = Set.of("api", "actuator", "error");

    private final LinkStore linkStore;
    private final URI publicBaseUri;
    private final Clock clock;

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    @Override
    public Link createLink(CreateLinkCommand command) {
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());
        rejectSelfTargetUrl(link.originalUrl());
        String normalizedTitle = normalizeTitle(command.title());
        List<String> normalizedTags = normalizeTags(command.tags());
        String hostname = extractHostname(link.originalUrl().value());

        if (!linkStore.save(link, command.expiresAt(), normalizedTitle, normalizedTags, hostname)) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }

        return link;
    }

    @Override
    public LinkDetails updateLink(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OriginalUrl validatedOriginalUrl = new OriginalUrl(originalUrl);
        rejectSelfTargetUrl(validatedOriginalUrl);
        String normalizedTitle = normalizeTitle(title);
        List<String> normalizedTags = normalizeTags(tags);
        String hostname = extractHostname(validatedOriginalUrl.value());

        if (!linkStore.update(
                linkSlug.value(),
                validatedOriginalUrl.value(),
                expiresAt,
                normalizedTitle,
                normalizedTags,
                hostname)) {
            throw new LinkNotFoundException(linkSlug.value());
        }

        return linkStore.findDetailsBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public void deleteLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        if (!linkStore.deleteBySlug(linkSlug.value())) {
            throw new LinkNotFoundException(linkSlug.value());
        }
    }

    @Override
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress) {
        linkStore.recordClick(new LinkClick(slug, now(), userAgent, referrer, remoteAddress));
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
}
