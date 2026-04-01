package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Optional;

public interface LinkStore {

    boolean save(Link link);

    boolean updateOriginalUrl(String slug, String originalUrl);

    boolean deleteBySlug(String slug);

    Optional<Link> findBySlug(String slug);

    Optional<LinkDetails> findDetailsBySlug(String slug);

    List<LinkDetails> findRecent(int limit);
}
