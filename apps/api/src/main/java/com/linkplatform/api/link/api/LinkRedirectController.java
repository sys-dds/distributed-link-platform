package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.RedirectDecision;
import com.linkplatform.api.link.application.RedirectRuntimeService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.REDIRECT})
public class LinkRedirectController {

    private final LinkApplicationService linkApplicationService;
    private final RedirectRuntimeService redirectRuntimeService;

    public LinkRedirectController(
            LinkApplicationService linkApplicationService,
            RedirectRuntimeService redirectRuntimeService) {
        this.linkApplicationService = linkApplicationService;
        this.redirectRuntimeService = redirectRuntimeService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Void> redirect(@PathVariable String slug, HttpServletRequest request) {
        RedirectDecision redirectDecision = redirectRuntimeService.resolve(
                slug,
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteAddr());
        if (redirectDecision.recordAnalytics()) {
            linkApplicationService.recordRedirectClick(
                    slug,
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getHeader(HttpHeaders.REFERER),
                    request.getRemoteAddr());
        }

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .header(HttpHeaders.LOCATION, redirectDecision.location())
                .build();
    }
}
