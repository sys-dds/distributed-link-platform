package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.OffsetDateTime;
import java.util.List;
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
    public boolean updateOriginalUrl(String slug, String originalUrl) {
        return jdbcTemplate.update(
                "UPDATE links SET original_url = ? WHERE slug = ?",
                originalUrl,
                slug) == 1;
    }

    @Override
    public boolean deleteBySlug(String slug) {
        return jdbcTemplate.update("DELETE FROM links WHERE slug = ?", slug) == 1;
    }

    @Override
    public Optional<Link> findBySlug(String slug) {
        return jdbcTemplate.query(
                        "SELECT slug, original_url FROM links WHERE slug = ?",
                        (resultSet, rowNum) -> toLink(resultSet.getString("slug"), resultSet.getString("original_url")),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findDetailsBySlug(String slug) {
        return jdbcTemplate.query(
                        "SELECT slug, original_url, created_at FROM links WHERE slug = ?",
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class)),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkDetails> findRecent(int limit) {
        return jdbcTemplate.query(
                """
                SELECT slug, original_url, created_at
                FROM links
                ORDER BY created_at DESC, slug ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> toLinkDetails(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getObject("created_at", OffsetDateTime.class)),
                limit);
    }

    private Link toLink(String slug, String originalUrl) {
        return new Link(new LinkSlug(slug), new OriginalUrl(originalUrl));
    }

    private LinkDetails toLinkDetails(String slug, String originalUrl, OffsetDateTime createdAt) {
        return new LinkDetails(slug, originalUrl, createdAt);
    }
}
