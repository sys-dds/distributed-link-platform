package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.CreateLinkCommand;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.domain.Link;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final LinkApplicationService linkApplicationService;

    public LinkController(LinkApplicationService linkApplicationService) {
        this.linkApplicationService = linkApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLinkResponse createLink(@RequestBody CreateLinkRequest request) {
        Link link = linkApplicationService.createLink(new CreateLinkCommand(request.slug(), request.originalUrl()));
        return new CreateLinkResponse(link.slug().value(), link.originalUrl().value());
    }

    @GetMapping("/{slug}")
    public LinkResponse getLink(@PathVariable String slug) {
        return toResponse(linkApplicationService.getLink(slug));
    }

    @GetMapping
    public List<LinkResponse> listLinks(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        validateLimit(limit);
        return linkApplicationService.listRecentLinks(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private LinkResponse toResponse(LinkDetails linkDetails) {
        return new LinkResponse(linkDetails.slug(), linkDetails.originalUrl(), linkDetails.createdAt());
    }
}
