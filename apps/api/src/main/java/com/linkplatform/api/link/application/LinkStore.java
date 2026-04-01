package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkStore {

    boolean save(Link link, OffsetDateTime expiresAt);

    boolean update(String slug, String originalUrl, OffsetDateTime expiresAt);

    boolean deleteBySlug(String slug);

    Optional<Link> findBySlug(String slug, OffsetDateTime now);

    Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now);

    List<LinkDetails> findRecent(int limit, OffsetDateTime now);
}
