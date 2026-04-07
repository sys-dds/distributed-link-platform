package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/bulk")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class BulkLinksController {

    private static final int MAX_SLUGS = 200;

    private final LinkApplicationService linkApplicationService;
    private final OwnerAccessService ownerAccessService;

    public BulkLinksController(
            LinkApplicationService linkApplicationService,
            OwnerAccessService ownerAccessService) {
        this.linkApplicationService = linkApplicationService;
        this.ownerAccessService = ownerAccessService;
    }

    @PostMapping("/actions")
    public BulkLinkActionResponse bulkAction(
            @RequestBody BulkLinkActionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        validateRequest(request);
        WorkspaceAccessContext owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.LINKS_WRITE);
        return new BulkLinkActionResponse(
                request.action().trim().toLowerCase(Locale.ROOT),
                linkApplicationService.bulkAction(
                                owner,
                                request.action(),
                                request.slugs(),
                                request.tags(),
                                request.expiresAt(),
                                idempotencyKey).stream()
                        .map(item -> new BulkLinkActionItemResponse(
                                item.slug(),
                                item.success(),
                                item.newVersion(),
                                item.errorCategory(),
                                item.errorDetail()))
                        .toList());
    }

    private void validateRequest(BulkLinkActionRequest request) {
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new IllegalArgumentException("Bulk action is required");
        }
        if (request.slugs() == null || request.slugs().isEmpty()) {
            throw new IllegalArgumentException("Bulk action requires at least one slug");
        }
        if (request.slugs().size() > MAX_SLUGS) {
            throw new IllegalArgumentException("Bulk action supports at most " + MAX_SLUGS + " slugs");
        }
        Set<String> seen = new HashSet<>();
        for (String slug : request.slugs()) {
            if (slug == null || slug.isBlank()) {
                throw new IllegalArgumentException("Bulk action slugs must be non-blank");
            }
            if (!seen.add(slug.trim())) {
                throw new IllegalArgumentException("Bulk action slugs must be unique");
            }
        }
        String action = request.action().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "archive", "suspend", "delete" -> {
                if (request.tags() != null) {
                    throw new IllegalArgumentException("tags is not allowed for action " + action);
                }
                if (request.expiresAt() != null) {
                    throw new IllegalArgumentException("expiresAt is not allowed for action " + action);
                }
            }
            case "update-tags" -> {
                if (request.tags() == null) {
                    throw new IllegalArgumentException("tags is required for action update-tags");
                }
                if (request.expiresAt() != null) {
                    throw new IllegalArgumentException("expiresAt is not allowed for action update-tags");
                }
            }
            case "update-expiry" -> {
                if (request.expiresAt() == null) {
                    throw new IllegalArgumentException("expiresAt is required for action update-expiry");
                }
                if (request.tags() != null) {
                    throw new IllegalArgumentException("tags is not allowed for action update-expiry");
                }
            }
            default -> throw new IllegalArgumentException(
                    "Bulk action must be one of: archive, suspend, delete, update-tags, update-expiry");
        }
    }
}
