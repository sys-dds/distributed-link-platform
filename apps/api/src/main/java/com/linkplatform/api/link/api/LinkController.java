package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.CreateLinkCommand;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkDiscoveryExpirationFilter;
import com.linkplatform.api.link.application.LinkDiscoveryLifecycleFilter;
import com.linkplatform.api.link.application.LinkDiscoveryPage;
import com.linkplatform.api.link.application.LinkDiscoveryQuery;
import com.linkplatform.api.link.application.LinkDiscoverySort;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkLifecycleState;
import com.linkplatform.api.link.application.LinkMutationResult;
import com.linkplatform.api.link.application.LinkPreconditionRequiredException;
import com.linkplatform.api.link.application.LinkSuggestion;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
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
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class LinkController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_SUGGESTION_LIMIT = 10;
    private static final int MAX_SUGGESTION_LIMIT = 20;
    private static final int DEFAULT_DISCOVERY_LIMIT = 20;
    private static final int MAX_DISCOVERY_LIMIT = 50;

    private final LinkApplicationService linkApplicationService;
    private final OwnerAccessService ownerAccessService;

    public LinkController(LinkApplicationService linkApplicationService, OwnerAccessService ownerAccessService) {
        this.linkApplicationService = linkApplicationService;
        this.ownerAccessService = ownerAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLinkResponse createLink(
            @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        LinkMutationResult result = linkApplicationService.createLink(
                ownerAccessService.authorizeMutation(
                        apiKey,
                        authorizationHeader,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
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
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        return toUpdateResponse(linkApplicationService.updateLink(
                ownerAccessService.authorizeMutation(
                        apiKey,
                        authorizationHeader,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
                slug,
                request.originalUrl(),
                request.expiresAt(),
                request.title(),
                request.tags(),
                parseIfMatch(ifMatch),
                idempotencyKey));
    }

    @GetMapping("/{slug}")
    public LinkReadResponse getLink(
            @PathVariable String slug,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        return toReadResponse(linkApplicationService.getLink(
                ownerAccessService.authorizeRead(
                        apiKey,
                        authorizationHeader,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
                slug));
    }

    @GetMapping
    public List<LinkReadResponse> listLinks(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "active") String state,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        validateLimit(limit);
        return linkApplicationService.listRecentLinks(
                        ownerAccessService.authorizeRead(
                                apiKey,
                                authorizationHeader,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr()),
                        limit,
                        resolveSearchText(q, search),
                        parseState(state)).stream()
                .map(this::toReadResponse)
                .toList();
    }

    @GetMapping("/suggestions")
    public List<LinkSuggestionResponse> suggestLinks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "" + DEFAULT_SUGGESTION_LIMIT) int limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        validateSuggestionLimit(limit);
        return linkApplicationService.suggestLinks(
                        ownerAccessService.authorizeRead(
                                apiKey,
                                authorizationHeader,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr()),
                        resolveSearchText(q, search),
                        limit).stream()
                .map(this::toSuggestionResponse)
                .toList();
    }

    @GetMapping("/discovery")
    public LinkDiscoveryPageResponse discoverLinks(
            @RequestParam(required = false, name = "q") String searchText,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "active") String lifecycle,
            @RequestParam(defaultValue = "any") String expiration,
            @RequestParam(defaultValue = "updated_desc") String sort,
            @RequestParam(defaultValue = "" + DEFAULT_DISCOVERY_LIMIT) int limit,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        validateDiscoveryLimit(limit);
        LinkDiscoveryPage page = linkApplicationService.searchLinks(
                ownerAccessService.authorizeRead(
                        apiKey,
                        authorizationHeader,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
                new LinkDiscoveryQuery(
                        resolveSearchText(searchText, search),
                        hostname,
                        tag,
                        parseDiscoveryLifecycle(lifecycle),
                        parseExpiration(expiration),
                        parseSort(sort),
                        limit,
                        cursor));
        return new LinkDiscoveryPageResponse(
                page.items().stream()
                        .map(item -> new LinkDiscoveryItemResponse(
                                item.slug(),
                                item.originalUrl(),
                                item.title(),
                                item.hostname(),
                                item.tags(),
                                item.lifecycleState().name().toLowerCase(Locale.ROOT),
                                item.createdAt(),
                                item.updatedAt(),
                                item.expiresAt(),
                                item.deletedAt(),
                                item.version()))
                        .toList(),
                page.nextCursor(),
                page.hasMore());
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(
            @PathVariable String slug,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        linkApplicationService.deleteLink(
                ownerAccessService.authorizeMutation(
                        apiKey,
                        authorizationHeader,
                        httpServletRequest.getMethod(),
                        httpServletRequest.getRequestURI(),
                        httpServletRequest.getRemoteAddr()),
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

    private void validateDiscoveryLimit(int limit) {
        if (limit < 1 || limit > MAX_DISCOVERY_LIMIT) {
            throw new IllegalArgumentException("Discovery limit must be between 1 and " + MAX_DISCOVERY_LIMIT);
        }
    }

    private LinkLifecycleState parseState(String state) {
        try {
            return LinkLifecycleState.valueOf(normalize(state).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("State must be one of: active, expired, all");
        }
    }

    private LinkDiscoveryLifecycleFilter parseDiscoveryLifecycle(String lifecycle) {
        try {
            return LinkDiscoveryLifecycleFilter.valueOf(normalize(lifecycle).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Lifecycle must be one of: active, expired, deleted, all");
        }
    }

    private LinkDiscoveryExpirationFilter parseExpiration(String expiration) {
        return switch (normalize(expiration).toLowerCase(Locale.ROOT)) {
            case "any" -> LinkDiscoveryExpirationFilter.ANY;
            case "scheduled" -> LinkDiscoveryExpirationFilter.SCHEDULED;
            case "none" -> LinkDiscoveryExpirationFilter.NONE;
            case "expired" -> LinkDiscoveryExpirationFilter.EXPIRED;
            default -> throw new IllegalArgumentException("Expiration must be one of: any, scheduled, none, expired");
        };
    }

    private LinkDiscoverySort parseSort(String sort) {
        return switch (normalize(sort).toLowerCase(Locale.ROOT)) {
            case "updated_desc" -> LinkDiscoverySort.UPDATED_DESC;
            case "created_desc" -> LinkDiscoverySort.CREATED_DESC;
            case "slug_asc" -> LinkDiscoverySort.SLUG_ASC;
            default -> throw new IllegalArgumentException("Sort must be one of: updated_desc, created_desc, slug_asc");
        };
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

    private String resolveSearchText(String q, String search) {
        String normalizedQ = normalizeToNull(q);
        if (normalizedQ != null) {
            return normalizedQ;
        }
        return normalizeToNull(search);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
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
