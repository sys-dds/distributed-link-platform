package com.linkplatform.api.owner.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
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
            HttpServletRequest httpServletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeRead(
                apiKey,
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
