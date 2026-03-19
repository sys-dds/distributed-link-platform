package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.CreateLinkCommand;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.domain.Link;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkApplicationService linkApplicationService;

    public LinkController(LinkApplicationService linkApplicationService) {
        this.linkApplicationService = linkApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLinkResponse createLink(@RequestBody CreateLinkRequest request) {
        Link link = linkApplicationService.createLink(new CreateLinkCommand(request.slug(), request.originalUrl()));
        return new CreateLinkResponse(link.slug().value(), link.originalUrl().value());
    }
}
