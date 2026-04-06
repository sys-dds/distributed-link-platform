package com.linkplatform.api.owner.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
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
            HttpServletRequest httpServletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return new MeResponse(
                owner.ownerKey(),
                owner.displayName(),
                owner.plan().name(),
                linkApplicationService.countActiveLinks(owner),
                owner.plan().activeLinkLimit());
    }
}
