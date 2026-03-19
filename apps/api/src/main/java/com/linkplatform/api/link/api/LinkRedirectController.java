package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.domain.Link;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LinkRedirectController {

    private final LinkApplicationService linkApplicationService;

    public LinkRedirectController(LinkApplicationService linkApplicationService) {
        this.linkApplicationService = linkApplicationService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Void> redirect(@PathVariable String slug) {
        Link link = linkApplicationService.resolveLink(slug);

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .header(HttpHeaders.LOCATION, link.originalUrl().value())
                .build();
    }
}
