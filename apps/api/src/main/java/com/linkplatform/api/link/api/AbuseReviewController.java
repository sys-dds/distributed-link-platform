package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkAbuseCaseStatus;
import com.linkplatform.api.link.application.LinkAbuseQueueQuery;
import com.linkplatform.api.link.application.LinkAbuseReviewService;
import com.linkplatform.api.link.application.LinkAbuseSource;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/abuse/reviews")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class AbuseReviewController {

    private final LinkAbuseReviewService linkAbuseReviewService;
    private final OwnerAccessService ownerAccessService;
    private final LinkPlatformRuntimeProperties runtimeProperties;

    public AbuseReviewController(
            LinkAbuseReviewService linkAbuseReviewService,
            OwnerAccessService ownerAccessService,
            LinkPlatformRuntimeProperties runtimeProperties) {
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.ownerAccessService = ownerAccessService;
        this.runtimeProperties = runtimeProperties;
    }

    @GetMapping
    public AbuseReviewQueueResponse listQueue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        var context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        int effectiveLimit = limit == null ? runtimeProperties.getAbuse().getReviewPageSizeDefault() : limit;
        if (effectiveLimit < 1 || effectiveLimit > runtimeProperties.getAbuse().getReviewPageSizeMax()) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and " + runtimeProperties.getAbuse().getReviewPageSizeMax());
        }
        LinkAbuseCaseStatus requestedStatus = status == null || status.isBlank()
                ? LinkAbuseCaseStatus.OPEN
                : LinkAbuseCaseStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        LinkAbuseSource requestedSource = source == null || source.isBlank()
                ? null
                : LinkAbuseSource.valueOf(source.trim().toUpperCase(java.util.Locale.ROOT));
        var page = linkAbuseReviewService.listQueue(
                context,
                new LinkAbuseQueueQuery(requestedStatus, requestedSource, slug, effectiveLimit, cursor));
        return new AbuseReviewQueueResponse(
                page.items().stream().map(AbuseReviewCaseResponse::from).toList(),
                page.nextCursor(),
                page.hasMore());
    }

    @PostMapping("/manual")
    public AbuseReviewCaseResponse createManualCase(
            @RequestBody ManualAbuseReviewRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        if (request == null || request.slug() == null || request.slug().isBlank() || request.summary() == null || request.summary().isBlank()) {
            throw new IllegalArgumentException("slug and summary are required");
        }
        var context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        return AbuseReviewCaseResponse.from(linkAbuseReviewService.createManualCase(
                context,
                request.slug().trim(),
                request.summary().trim(),
                request.detailSummary(),
                request.riskScore(),
                request.quarantineNowValue()));
    }

    @PostMapping("/{caseId}/quarantine")
    public AbuseReviewCaseResponse quarantine(
            @PathVariable long caseId,
            @RequestBody(required = false) ResolveAbuseReviewRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return resolve(apiKey, authorizationHeader, workspaceSlug, httpServletRequest, caseId, request, ResolutionAction.QUARANTINE);
    }

    @PostMapping("/{caseId}/release")
    public AbuseReviewCaseResponse release(
            @PathVariable long caseId,
            @RequestBody(required = false) ResolveAbuseReviewRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return resolve(apiKey, authorizationHeader, workspaceSlug, httpServletRequest, caseId, request, ResolutionAction.RELEASE);
    }

    @PostMapping("/{caseId}/dismiss")
    public AbuseReviewCaseResponse dismiss(
            @PathVariable long caseId,
            @RequestBody(required = false) ResolveAbuseReviewRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        return resolve(apiKey, authorizationHeader, workspaceSlug, httpServletRequest, caseId, request, ResolutionAction.DISMISS);
    }

    private AbuseReviewCaseResponse resolve(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest httpServletRequest,
            long caseId,
            ResolveAbuseReviewRequest request,
            ResolutionAction action) {
        var context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        String note = request == null ? null : request.resolutionNote();
        return switch (action) {
            case QUARANTINE -> AbuseReviewCaseResponse.from(linkAbuseReviewService.quarantineCase(context, caseId, note));
            case RELEASE -> AbuseReviewCaseResponse.from(linkAbuseReviewService.releaseCase(context, caseId, note));
            case DISMISS -> AbuseReviewCaseResponse.from(linkAbuseReviewService.dismissCase(context, caseId, note));
        };
    }

    private enum ResolutionAction {
        QUARANTINE,
        RELEASE,
        DISMISS
    }
}
