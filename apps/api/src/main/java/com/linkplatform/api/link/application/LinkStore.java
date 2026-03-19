package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.Optional;

public interface LinkStore {

    boolean save(Link link);

    Optional<Link> findBySlug(String slug);
}
