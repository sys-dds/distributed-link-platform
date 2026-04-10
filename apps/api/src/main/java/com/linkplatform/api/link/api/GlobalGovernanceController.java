package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.GovernanceRollupStore;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/governance")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class GlobalGovernanceController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final OwnerAccessService ownerAccessService;
    private final GovernanceRollupStore governanceRollupStore;
    private final Clock clock;

    public GlobalGovernanceController(
            OwnerAccessService ownerAccessService,
            GovernanceRollupStore governanceRollupStore,
            Clock clock) {
        this.ownerAccessService = ownerAccessService;
        this.governanceRollupStore = governanceRollupStore;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public GlobalGovernanceSummaryResponse summary(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        authorizeOpsRead(apiKey, authorizationHeader, workspaceSlug, request);
        return GlobalGovernanceSummaryResponse.from(governanceRollupStore.summary(OffsetDateTime.now(clock)));
    }

    @GetMapping("/webhooks/risk")
    public GlobalWebhookRiskResponse webhookRisk(
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        authorizeOpsRead(apiKey, authorizationHeader, workspaceSlug, request);
        return GlobalWebhookRiskResponse.from(governanceRollupStore.webhookRisk(resolveLimit(limit)));
    }

    @GetMapping("/abuse/risk")
    public GlobalAbuseRiskResponse abuseRisk(
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        authorizeOpsRead(apiKey, authorizationHeader, workspaceSlug, request);
        return GlobalAbuseRiskResponse.from(governanceRollupStore.abuseRisk(resolveLimit(limit)));
    }

    @GetMapping("/over-quota")
    public OverQuotaWorkspaceResponse overQuota(
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        authorizeOpsRead(apiKey, authorizationHeader, workspaceSlug, request);
        return OverQuotaWorkspaceResponse.from(governanceRollupStore.overQuota(resolveLimit(limit)));
    }

    private void authorizeOpsRead(
            String apiKey,
            String authorizationHeader,
            String workspaceSlug,
            HttpServletRequest request) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
