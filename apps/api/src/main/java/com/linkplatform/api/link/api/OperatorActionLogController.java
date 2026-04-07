package com.linkplatform.api.link.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.JdbcOperatorActionLogStore;
import com.linkplatform.api.owner.application.OperatorActionLogQuery;
import com.linkplatform.api.owner.application.OperatorActionLogStore;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/actions")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class OperatorActionLogController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final OwnerAccessService ownerAccessService;
    private final OperatorActionLogStore operatorActionLogStore;

    public OperatorActionLogController(
            OwnerAccessService ownerAccessService,
            OperatorActionLogStore operatorActionLogStore) {
        this.ownerAccessService = ownerAccessService;
        this.operatorActionLogStore = operatorActionLogStore;
    }

    @GetMapping
    public OperatorActionLogPageResponse listActions(
            @RequestParam(required = false) String subsystem,
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
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
        List<com.linkplatform.api.owner.application.OperatorActionLogRecord> fetched = operatorActionLogStore.findRecent(
                context.workspaceId(),
                new OperatorActionLogQuery(
                        subsystem == null || subsystem.isBlank() ? null : subsystem.trim().toUpperCase(java.util.Locale.ROOT),
                        effectiveLimit,
                        cursor));
        boolean hasMore = fetched.size() > effectiveLimit;
        List<com.linkplatform.api.owner.application.OperatorActionLogRecord> items = hasMore ? fetched.subList(0, effectiveLimit) : fetched;
        String nextCursor = hasMore ? JdbcOperatorActionLogStore.encodeCursor(items.get(items.size() - 1)) : null;
        return new OperatorActionLogPageResponse(
                items.stream().map(OperatorActionLogResponse::from).toList(),
                nextCursor,
                hasMore);
    }
}
