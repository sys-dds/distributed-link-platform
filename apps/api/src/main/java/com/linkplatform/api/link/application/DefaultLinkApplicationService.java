package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private static final Set<String> RESERVED_TOP_LEVEL_SLUGS = Set.of("api", "actuator", "error");

    private final LinkStore linkStore;

    public DefaultLinkApplicationService(LinkStore linkStore) {
        this.linkStore = linkStore;
    }

    @Override
    public Link createLink(CreateLinkCommand command) {
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());

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

    private void rejectReservedSlug(LinkSlug slug) {
        String normalizedSlug = slug.value().toLowerCase(Locale.ROOT);

        if (RESERVED_TOP_LEVEL_SLUGS.contains(normalizedSlug)) {
            throw new ReservedLinkSlugException(slug.value());
        }
    }
}
