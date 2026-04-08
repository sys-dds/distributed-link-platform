package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.WorkspaceAbuseIntelligenceService;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/current/abuse")
public class WorkspaceAbuseIntelligenceController {

    private final OwnerAccessService ownerAccessService;
    private final WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService;

    public WorkspaceAbuseIntelligenceController(
            OwnerAccessService ownerAccessService,
            WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService) {
        this.ownerAccessService = ownerAccessService;
        this.workspaceAbuseIntelligenceService = workspaceAbuseIntelligenceService;
    }

    @GetMapping("/policy")
    public WorkspaceAbusePolicyResponse policy(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_READ);
        return WorkspaceAbusePolicyResponse.from(workspaceAbuseIntelligenceService.currentPolicy(context));
    }

    @PatchMapping("/policy")
    public WorkspaceAbusePolicyResponse updatePolicy(
            @RequestBody UpdateWorkspaceAbusePolicyRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_WRITE);
        return WorkspaceAbusePolicyResponse.from(workspaceAbuseIntelligenceService.updatePolicy(
                context,
                requestBody == null ? null : requestBody.rawIpReviewEnabled(),
                requestBody == null ? null : requestBody.punycodeReviewEnabled(),
                requestBody == null ? null : requestBody.repeatedHostQuarantineThreshold(),
                requestBody == null ? null : requestBody.redirectRateLimitQuarantineThreshold()));
    }

    @GetMapping("/host-rules")
    public List<WorkspaceHostRuleResponse> hostRules(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_READ);
        return workspaceAbuseIntelligenceService.listHostRules(context).stream().map(WorkspaceHostRuleResponse::from).toList();
    }

    @PostMapping("/host-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceHostRuleResponse createHostRule(
            @RequestBody CreateWorkspaceHostRuleRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_WRITE);
        return WorkspaceHostRuleResponse.from(workspaceAbuseIntelligenceService.createHostRule(
                context,
                requestBody == null ? null : requestBody.host(),
                requestBody == null ? null : requestBody.ruleType(),
                requestBody == null ? null : requestBody.note()));
    }

    @DeleteMapping("/host-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHostRule(
            @PathVariable long ruleId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_WRITE);
        workspaceAbuseIntelligenceService.deleteHostRule(context, ruleId);
    }

    @GetMapping("/trends")
    public WorkspaceAbuseTrendResponse trends(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        var context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.OPS_READ);
        return WorkspaceAbuseTrendResponse.from(workspaceAbuseIntelligenceService.trends(context));
    }
}
