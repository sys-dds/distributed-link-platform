package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public boolean save(Link link, OffsetDateTime expiresAt) {
        try {
            return jdbcTemplate.update(
                    "INSERT INTO links (slug, original_url, expires_at) VALUES (?, ?, ?)",
                    link.slug().value(),
                    link.originalUrl().value(),
                    expiresAt) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean update(String slug, String originalUrl, OffsetDateTime expiresAt) {
        return jdbcTemplate.update(
                "UPDATE links SET original_url = ?, expires_at = ? WHERE slug = ?",
                originalUrl,
                expiresAt,
                slug) == 1;
    }

    @Override
    public boolean deleteBySlug(String slug) {
        return jdbcTemplate.update("DELETE FROM links WHERE slug = ?", slug) == 1;
    }

    @Override
    public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
        return jdbcTemplate.query(
                        """
                        SELECT slug, original_url
                        FROM links
                        WHERE slug = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLink(resultSet.getString("slug"), resultSet.getString("original_url")),
                        slug,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now) {
        return jdbcTemplate.query(
                        """
                        SELECT slug, original_url, created_at, expires_at
                        FROM links
                        WHERE slug = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class)),
                        slug,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state) {
        StringBuilder sql = new StringBuilder("""
                SELECT slug, original_url, created_at, expires_at
                FROM links
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        appendLifecycleClause(sql, parameters, now, state);
        appendSearchClause(sql, parameters, query);

        sql.append("""
                
                ORDER BY created_at DESC, slug ASC
                LIMIT ?
                """);
        parameters.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> toLinkDetails(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("expires_at", OffsetDateTime.class)),
                parameters.toArray());
    }

    private void appendLifecycleClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime now,
            LinkLifecycleState state) {
        switch (state) {
            case ACTIVE -> {
                sql.append("""
                        
                          AND (expires_at IS NULL OR expires_at > ?)
                        """);
                parameters.add(now);
            }
            case EXPIRED -> {
                sql.append("""
                        
                          AND expires_at IS NOT NULL
                          AND expires_at <= ?
                        """);
                parameters.add(now);
            }
            case ALL -> {
                // no lifecycle filter
            }
        }
    }

    private void appendSearchClause(StringBuilder sql, List<Object> parameters, String query) {
        if (query == null || query.isBlank()) {
            return;
        }

        sql.append("""
                
                  AND (
                    LOWER(slug) LIKE ?
                    OR LOWER(original_url) LIKE ?
                  )
                """);
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
        parameters.add(pattern);
        parameters.add(pattern);
    }

    private Link toLink(String slug, String originalUrl) {
        return new Link(new LinkSlug(slug), new OriginalUrl(originalUrl));
    }

    private LinkDetails toLinkDetails(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
        return new LinkDetails(slug, originalUrl, createdAt, expiresAt);
    }
}
