package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.AnalyticsRange;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkTrafficWindow;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.link.application.OwnerTrafficTotals;
import com.linkplatform.api.link.application.TopReferrer;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class LinkAnalyticsController {

    private static final int DEFAULT_ACTIVITY_LIMIT = 20;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int DEFAULT_TRENDING_LIMIT = 10;

    private final LinkApplicationService linkApplicationService;
    private final OwnerAccessService ownerAccessService;
    private final LinkStore linkStore;

    public LinkAnalyticsController(
            LinkApplicationService linkApplicationService,
            OwnerAccessService ownerAccessService,
            LinkStore linkStore) {
        this.linkApplicationService = linkApplicationService;
        this.ownerAccessService = ownerAccessService;
        this.linkStore = linkStore;
    }

    @GetMapping("/{slug}/traffic-summary")
    public LinkTrafficSummaryResponse getTrafficSummary(
            @PathVariable String slug,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "false") boolean comparePrevious,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var owner = authorizeAnalyticsRead(apiKey, authorizationHeader, workspaceSlug, httpServletRequest);
        return toResponse(
                linkApplicationService.getTrafficSummary(owner, slug, AnalyticsRange.optional(from, to, comparePrevious)),
                slug,
                owner.workspaceId());
    }

    @GetMapping("/{slug}/traffic-series")
    public LinkTrafficSeriesResponse getTrafficSeries(
            @PathVariable String slug,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam String granularity,
            @RequestParam(defaultValue = "false") boolean comparePrevious,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return LinkTrafficSeriesResponse.from(linkApplicationService.getTrafficSeries(
                authorizeAnalyticsRead(apiKey, authorizationHeader, workspaceSlug, httpServletRequest),
                slug,
                AnalyticsRange.required(from, to, comparePrevious),
                granularity));
    }

    @GetMapping("/traffic/top")
    public List<TopLinkTrafficResponse> getTopLinks(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(defaultValue = "" + DEFAULT_TOP_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        AnalyticsRange range = AnalyticsRange.optional(from, to, false);
        return linkApplicationService.getTopLinks(
                        authorizeAnalyticsRead(apiKey, authorizationHeader, workspaceSlug, httpServletRequest),
                        range == null ? parseWindow(window) : LinkTrafficWindow.LAST_7_DAYS,
                        range,
                        tag,
                        lifecycle,
                        limit).stream()
                .map(TopLinkTrafficResponse::from)
                .toList();
    }

    @GetMapping("/activity")
    public List<LinkActivityEventResponse> getRecentActivity(
            @RequestParam(defaultValue = "" + DEFAULT_ACTIVITY_LIMIT) int limit,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String lifecycle,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return linkApplicationService.getRecentActivity(
                        authorizeAnalyticsRead(apiKey, authorizationHeader, workspaceSlug, httpServletRequest),
                        limit,
                        tag,
                        lifecycle).stream()
                .map(LinkActivityEventResponse::from)
                .toList();
    }

    @GetMapping("/traffic/trending")
    public List<TrendingLinkResponse> getTrendingLinks(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "false") boolean comparePrevious,
            @RequestParam(defaultValue = "" + DEFAULT_TRENDING_LIMIT) int limit,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String lifecycle,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        AnalyticsRange range = AnalyticsRange.optional(from, to, comparePrevious);
        return linkApplicationService.getTrendingLinks(
                        authorizeAnalyticsRead(apiKey, authorizationHeader, workspaceSlug, httpServletRequest),
                        range == null ? parseWindow(window) : LinkTrafficWindow.LAST_7_DAYS,
                        range,
                        tag,
                        lifecycle,
                        limit).stream()
                .map(TrendingLinkResponse::from)
                .toList();
    }

    private LinkTrafficWindow parseWindow(String window) {
        String normalized = normalize(window).toLowerCase();
        return switch (normalized) {
            case "24h" -> LinkTrafficWindow.LAST_24_HOURS;
            case "7d" -> LinkTrafficWindow.LAST_7_DAYS;
            default -> throw new IllegalArgumentException("Window must be one of: 24h, 7d");
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private WorkspaceAccessContext authorizeAnalyticsRead(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.ANALYTICS_READ);
    }

    private LinkTrafficSummaryResponse toResponse(
            LinkApplicationService.AnalyticsSummaryView summaryView,
            String slug,
            long ownerId) {
        List<TopReferrerResponse> topReferrers = linkStore.findTopReferrers(slug, 5, ownerId).stream()
                .map(this::toResponse)
                .toList();
        OwnerTrafficTotals ownerTrafficTotals = linkStore.findOwnerTrafficTotals(
                java.time.OffsetDateTime.now().minusHours(1),
                java.time.OffsetDateTime.now().minusHours(24),
                java.time.OffsetDateTime.now().toLocalDate().minusDays(6),
                ownerId);
        return new LinkTrafficSummaryResponse(
                summaryView.summary().slug(),
                summaryView.summary().originalUrl(),
                summaryView.summary().totalClicks(),
                summaryView.summary().clicksLast24Hours(),
                summaryView.summary().clicksLast7Days(),
                summaryView.summary().recentDailyClicks().stream()
                        .map(bucket -> new DailyClickBucketResponse(bucket.day(), bucket.clickTotal()))
                        .toList(),
                topReferrers,
                new LinkTrafficBreakdownResponse(
                        linkStore.findRecentHourlyClickBuckets(slug, java.time.OffsetDateTime.now().minusHours(24), ownerId).stream()
                                .map(bucket -> new DailyClickBucketResponse(bucket.day(), bucket.clickTotal()))
                                .toList(),
                        ownerTrafficTotals.clicksLast1Hour(),
                        ownerTrafficTotals.clicksLast24Hours(),
                        ownerTrafficTotals.clicksLast7Days()),
                summaryView.windowStart(),
                summaryView.windowEnd(),
                summaryView.windowClicks(),
                AnalyticsFreshnessResponse.from(summaryView.freshness()),
                AnalyticsComparisonResponse.from(summaryView.comparison()));
    }

    private TopReferrerResponse toResponse(TopReferrer topReferrer) {
        return new TopReferrerResponse(topReferrer.referrer(), topReferrer.clickTotal());
    }
}
