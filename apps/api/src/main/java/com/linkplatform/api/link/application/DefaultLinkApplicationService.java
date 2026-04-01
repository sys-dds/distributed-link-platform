package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultLinkApplicationService implements LinkApplicationService {

    private static final Set<String> RESERVED_TOP_LEVEL_SLUGS = Set.of("api", "actuator", "error");

    private final LinkStore linkStore;
    private final URI publicBaseUri;
    private final Clock clock;

    public DefaultLinkApplicationService(
            LinkStore linkStore,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    @Override
    public Link createLink(CreateLinkCommand command) {
        Link link = new Link(new LinkSlug(command.slug()), new OriginalUrl(command.originalUrl()));
        rejectReservedSlug(link.slug());
        rejectSelfTargetUrl(link.originalUrl());

        if (!linkStore.save(link, command.expiresAt())) {
            throw new DuplicateLinkSlugException(link.slug().value());
        }

        return link;
    }

    @Override
    public LinkDetails updateLink(String slug, String originalUrl, OffsetDateTime expiresAt) {
        LinkSlug linkSlug = new LinkSlug(slug);
        OriginalUrl validatedOriginalUrl = new OriginalUrl(originalUrl);
        rejectSelfTargetUrl(validatedOriginalUrl);

        if (!linkStore.update(linkSlug.value(), validatedOriginalUrl.value(), expiresAt)) {
            throw new LinkNotFoundException(linkSlug.value());
        }

        return linkStore.findDetailsBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public void deleteLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        if (!linkStore.deleteBySlug(linkSlug.value())) {
            throw new LinkNotFoundException(linkSlug.value());
        }
    }

    @Override
    public Link resolveLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public LinkDetails getLink(String slug) {
        LinkSlug linkSlug = new LinkSlug(slug);

        return linkStore.findDetailsBySlug(linkSlug.value(), now())
                .orElseThrow(() -> new LinkNotFoundException(linkSlug.value()));
    }

    @Override
    public List<LinkDetails> listRecentLinks(int limit) {
        return linkStore.findRecent(limit, now());
    }

    private void rejectReservedSlug(LinkSlug slug) {
        String normalizedSlug = slug.value().toLowerCase(Locale.ROOT);

        if (RESERVED_TOP_LEVEL_SLUGS.contains(normalizedSlug)) {
            throw new ReservedLinkSlugException(slug.value());
        }
    }

    private void rejectSelfTargetUrl(OriginalUrl originalUrl) {
        URI targetUri = URI.create(originalUrl.value());

        if (isSameOrigin(publicBaseUri, targetUri)) {
            throw new SelfTargetLinkException(originalUrl.value());
        }
    }

    private boolean isSameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }

        return 80;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
