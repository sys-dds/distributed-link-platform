package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.CreateLinkCommand;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkLifecycleState;
import com.linkplatform.api.link.application.LinkMutationResult;
import com.linkplatform.api.link.application.LinkPreconditionRequiredException;
import com.linkplatform.api.link.application.LinkSuggestion;
import com.linkplatform.api.owner.application.ApiKeyAuthenticationService;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_SUGGESTION_LIMIT = 10;
    private static final int MAX_SUGGESTION_LIMIT = 20;

    private final LinkApplicationService linkApplicationService;
    private final ApiKeyAuthenticationService apiKeyAuthenticationService;

    public LinkController(
            LinkApplicationService linkApplicationService,
            ApiKeyAuthenticationService apiKeyAuthenticationService) {
        this.linkApplicationService = linkApplicationService;
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLinkResponse createLink(
            @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        AuthenticatedOwner owner = apiKeyAuthenticationService.authenticate(apiKey);
        LinkMutationResult result = linkApplicationService.createLink(
                owner,
                new CreateLinkCommand(
                        request.slug(),
                        request.originalUrl(),
                        request.expiresAt(),
                        request.title(),
                        request.tags()),
                idempotencyKey);
        return new CreateLinkResponse(result.slug(), result.originalUrl(), result.version());
    }

    @PutMapping("/{slug}")
    public LinkResponse updateLink(
            @PathVariable String slug,
            @RequestBody UpdateLinkRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        AuthenticatedOwner owner = apiKeyAuthenticationService.authenticate(apiKey);
        return toUpdateResponse(linkApplicationService.updateLink(
                owner,
                slug,
                request.originalUrl(),
                request.expiresAt(),
                request.title(),
                request.tags(),
                parseIfMatch(ifMatch),
                idempotencyKey));
    }

    @GetMapping("/{slug}")
    public LinkReadResponse getLink(@PathVariable String slug) {
        return toReadResponse(linkApplicationService.getLink(slug));
    }

    @GetMapping
    public List<LinkReadResponse> listLinks(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "active") String state) {
        validateLimit(limit);
        return linkApplicationService.listRecentLinks(limit, q, parseState(state)).stream()
                .map(this::toReadResponse)
                .toList();
    }

    @GetMapping("/suggestions")
    public List<LinkSuggestionResponse> suggestLinks(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "" + DEFAULT_SUGGESTION_LIMIT) int limit) {
        validateSuggestionLimit(limit);
        return linkApplicationService.suggestLinks(q, limit).stream()
                .map(this::toSuggestionResponse)
                .toList();
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(
            @PathVariable String slug,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        linkApplicationService.deleteLink(
                apiKeyAuthenticationService.authenticate(apiKey),
                slug,
                parseIfMatch(ifMatch),
                idempotencyKey);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private void validateSuggestionLimit(int limit) {
        if (limit < 1 || limit > MAX_SUGGESTION_LIMIT) {
            throw new IllegalArgumentException("Suggestion limit must be between 1 and " + MAX_SUGGESTION_LIMIT);
        }
    }

    private LinkLifecycleState parseState(String state) {
        try {
            return LinkLifecycleState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("State must be one of: active, expired, all");
        }
    }

    private long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new LinkPreconditionRequiredException("If-Match header is required");
        }
        try {
            return Long.parseLong(ifMatch.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match header must be a plain integer version");
        }
    }

    private LinkResponse toUpdateResponse(LinkMutationResult result) {
        return new LinkResponse(
                result.slug(),
                result.originalUrl(),
                result.createdAt(),
                result.expiresAt(),
                result.title(),
                result.tags(),
                result.hostname(),
                result.version());
    }

    private LinkReadResponse toReadResponse(LinkDetails linkDetails) {
        return new LinkReadResponse(
                linkDetails.slug(),
                linkDetails.originalUrl(),
                linkDetails.createdAt(),
                linkDetails.expiresAt(),
                linkDetails.title(),
                linkDetails.tags(),
                linkDetails.hostname(),
                linkDetails.version(),
                linkDetails.clickTotal());
    }

    private LinkSuggestionResponse toSuggestionResponse(LinkSuggestion linkSuggestion) {
        return new LinkSuggestionResponse(linkSuggestion.slug(), linkSuggestion.title(), linkSuggestion.hostname());
    }
}
