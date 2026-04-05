package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkActivityEvent;
import com.linkplatform.api.link.application.LinkTrafficSummary;
import com.linkplatform.api.link.application.LinkTrafficWindow;
import com.linkplatform.api.link.application.TrendingLink;
import com.linkplatform.api.link.application.TopLinkTraffic;
import com.linkplatform.api.owner.application.OwnerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkAnalyticsController {

    private static final int DEFAULT_ACTIVITY_LIMIT = 20;
    private static final int DEFAULT_TRENDING_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final LinkApplicationService linkApplicationService;
    private final OwnerAccessService ownerAccessService;

    public LinkAnalyticsController(LinkApplicationService linkApplicationService, OwnerAccessService ownerAccessService) {
        this.linkApplicationService = linkApplicationService;
        this.ownerAccessService = ownerAccessService;
    }

    @GetMapping("/{slug}/traffic-summary")
    public LinkTrafficSummaryResponse getTrafficSummary(
            @PathVariable String slug,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        return toResponse(linkApplicationService.getTrafficSummary(
                ownerAccessService.authorizeRead(
                        apiKey,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
                slug));
    }

    @GetMapping("/traffic/top")
    public List<TopLinkTrafficResponse> getTopLinks(
            @RequestParam(defaultValue = "7d") String window,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        return linkApplicationService.getTopLinks(
                        ownerAccessService.authorizeRead(
                                apiKey,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr()),
                        parseWindow(window)).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/activity")
    public List<LinkActivityEventResponse> getRecentActivity(
            @RequestParam(defaultValue = "" + DEFAULT_ACTIVITY_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        validateLimit(limit);
        return linkApplicationService.getRecentActivity(
                        ownerAccessService.authorizeRead(
                                apiKey,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr()),
                        limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/traffic/trending")
    public List<TrendingLinkResponse> getTrendingLinks(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(defaultValue = "" + DEFAULT_TRENDING_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest httpServletRequest) {
        validateLimit(limit);
        return linkApplicationService.getTrendingLinks(
                        ownerAccessService.authorizeRead(
                                apiKey,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr()),
                        parseWindow(window),
                        limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private LinkTrafficWindow parseWindow(String window) {
        return switch (window) {
            case "24h" -> LinkTrafficWindow.LAST_24_HOURS;
            case "7d" -> LinkTrafficWindow.LAST_7_DAYS;
            default -> throw new IllegalArgumentException("Window must be one of: 24h, 7d");
        };
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private LinkTrafficSummaryResponse toResponse(LinkTrafficSummary summary) {
        return new LinkTrafficSummaryResponse(
                summary.slug(),
                summary.originalUrl(),
                summary.totalClicks(),
                summary.clicksLast24Hours(),
                summary.clicksLast7Days(),
                summary.recentDailyClicks().stream()
                        .map(bucket -> new DailyClickBucketResponse(bucket.day(), bucket.clickTotal()))
                        .toList());
    }

    private TopLinkTrafficResponse toResponse(TopLinkTraffic topLinkTraffic) {
        return new TopLinkTrafficResponse(
                topLinkTraffic.slug(),
                topLinkTraffic.originalUrl(),
                topLinkTraffic.clickTotal());
    }

    private LinkActivityEventResponse toResponse(LinkActivityEvent linkActivityEvent) {
        return new LinkActivityEventResponse(
                linkActivityEvent.type().name().toLowerCase(),
                linkActivityEvent.slug(),
                linkActivityEvent.originalUrl(),
                linkActivityEvent.title(),
                linkActivityEvent.tags(),
                linkActivityEvent.hostname(),
                linkActivityEvent.expiresAt(),
                linkActivityEvent.occurredAt());
    }

    private TrendingLinkResponse toResponse(TrendingLink trendingLink) {
        return new TrendingLinkResponse(
                trendingLink.slug(),
                trendingLink.originalUrl(),
                trendingLink.clickGrowth(),
                trendingLink.currentWindowClicks(),
                trendingLink.previousWindowClicks());
    }
}
