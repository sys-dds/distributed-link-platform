package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import org.springframework.stereotype.Service;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    @Override
    public Link prepareLink(CreateLinkCommand command) {
        return new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
    }
}
