package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresLinkStore implements LinkStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresLinkStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(Link link) {
        try {
            return jdbcTemplate.update(
                    "INSERT INTO links (slug, original_url) VALUES (?, ?)",
                    link.slug().value(),
                    link.originalUrl().value()) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<Link> findBySlug(String slug) {
        return jdbcTemplate.query(
                        "SELECT slug, original_url FROM links WHERE slug = ?",
                        (resultSet, rowNum) -> new Link(
                                new LinkSlug(resultSet.getString("slug")),
                                new OriginalUrl(resultSet.getString("original_url"))),
                        slug)
                .stream()
                .findFirst();
    }
}
