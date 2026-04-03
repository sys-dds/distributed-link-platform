package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkTrafficSummary;
import com.linkplatform.api.link.application.LinkTrafficWindow;
import com.linkplatform.api.link.application.TopLinkTraffic;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkAnalyticsController {

    private final LinkApplicationService linkApplicationService;

    public LinkAnalyticsController(LinkApplicationService linkApplicationService) {
        this.linkApplicationService = linkApplicationService;
    }

    @GetMapping("/{slug}/traffic-summary")
    public LinkTrafficSummaryResponse getTrafficSummary(@PathVariable String slug) {
        return toResponse(linkApplicationService.getTrafficSummary(slug));
    }

    @GetMapping("/traffic/top")
    public List<TopLinkTrafficResponse> getTopLinks(@RequestParam(defaultValue = "7d") String window) {
        return linkApplicationService.getTopLinks(parseWindow(window)).stream()
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
}
