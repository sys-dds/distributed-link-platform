package com.linkplatform.api.owner.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class MeController {

    private final OwnerAccessService ownerAccessService;
    private final LinkApplicationService linkApplicationService;

    public MeController(OwnerAccessService ownerAccessService, LinkApplicationService linkApplicationService) {
        this.ownerAccessService = ownerAccessService;
        this.linkApplicationService = linkApplicationService;
    }

    @GetMapping
    public MeResponse me(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.LINKS_READ);
        return new MeResponse(
                context.ownerKey(),
                context.displayName(),
                context.plan().name().toLowerCase(Locale.ROOT),
                linkApplicationService.countActiveLinks(context),
                context.plan().activeLinkLimit(),
                context.workspaceSlug(),
                context.role().name().toLowerCase(Locale.ROOT),
                context.grantedScopes().stream().map(ApiKeyScope::value).sorted(Comparator.naturalOrder()).toList(),
                ownerAccessService.authorizeAuthenticated(
                                apiKey,
                                authorizationHeader,
                                null,
                                httpServletRequest.getMethod(),
                                httpServletRequest.getRequestURI(),
                                httpServletRequest.getRemoteAddr())
                        .workspaceSlug());
    }
}
