package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryLinkStore implements LinkStore {

    private final ConcurrentHashMap<String, Link> linksBySlug = new ConcurrentHashMap<>();

    @Override
    public boolean save(Link link) {
        return linksBySlug.putIfAbsent(link.slug().value(), link) == null;
    }

    @Override
    public Optional<Link> findBySlug(String slug) {
        return Optional.ofNullable(linksBySlug.get(slug));
    }
}
