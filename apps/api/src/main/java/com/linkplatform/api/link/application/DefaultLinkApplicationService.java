package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import org.springframework.stereotype.Service;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private final LinkStore linkStore;

    public DefaultLinkApplicationService(LinkStore linkStore) {
        this.linkStore = linkStore;
    }

    @Override
    public Link createLink(CreateLinkCommand command) {
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));

        if (!linkStore.save(link)) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }

        return link;
    }

    @Override
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findBySlug(linkSlug.value())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }
}
