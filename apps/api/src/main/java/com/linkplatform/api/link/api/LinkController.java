package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.CreateLinkCommand;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkLifecycleState;
import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        Link link = linkApplicationService.createLink(
                new CreateLinkCommand(request.slug(), request.originalUrl(), request.expiresAt()));
        return new CreateLinkResponse(link.slug().value(), link.originalUrl().value());
    }

    @PutMapping("/{slug}")
    public LinkResponse updateLink(@PathVariable String slug, @RequestBody UpdateLinkRequest request) {
        return toResponse(linkApplicationService.updateLink(slug, request.originalUrl(), request.expiresAt()));
    }

    @GetMapping("/{slug}")
    public LinkResponse getLink(@PathVariable String slug) {
        return toResponse(linkApplicationService.getLink(slug));
    }

    @GetMapping
    public List<LinkResponse> listLinks(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "active") String state) {
        validateLimit(limit);
        return linkApplicationService.listRecentLinks(limit, q, parseState(state)).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable String slug) {
        linkApplicationService.deleteLink(slug);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private LinkLifecycleState parseState(String state) {
        try {
            return LinkLifecycleState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("State must be one of: active, expired, all");
        }
    }

    private LinkResponse toResponse(LinkDetails linkDetails) {
        return new LinkResponse(
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.createdAt(),
                linkDetails.expiresAt());
    }
}
