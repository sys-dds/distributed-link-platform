package com.linkplatform.api.link.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
public class JdbcRedirectRateLimitStore implements RedirectRateLimitStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRedirectRateLimitStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int increment(String subjectHash, String slug, OffsetDateTime bucketStartedAt, Duration window, OffsetDateTime expiresAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE redirect_rate_limits
                SET request_count = request_count + 1, expires_at = ?
                WHERE subject_hash = ?
                  AND ((slug IS NULL AND ? IS NULL) OR slug = ?)
                  AND bucket_started_at = ?
                  AND window_seconds = ?
                """,
                expiresAt,
                subjectHash,
                slug,
                slug,
                bucketStartedAt,
                Math.toIntExact(window.getSeconds()));
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO redirect_rate_limits (
                        subject_hash, slug, bucket_started_at, window_seconds, request_count, expires_at
                    )
                    VALUES (?, ?, ?, ?, 1, ?)
                    ON CONFLICT (subject_hash, slug, bucket_started_at, window_seconds) DO UPDATE
                    SET request_count = redirect_rate_limits.request_count + 1,
                        expires_at = EXCLUDED.expires_at
                    """,
                    subjectHash,
                    slug,
                    bucketStartedAt,
                    Math.toIntExact(window.getSeconds()),
                    expiresAt);
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT request_count
                FROM redirect_rate_limits
                WHERE subject_hash = ?
                  AND ((slug IS NULL AND ? IS NULL) OR slug = ?)
                  AND bucket_started_at = ?
                  AND window_seconds = ?
                """,
                Integer.class,
                subjectHash,
                slug,
                slug,
                bucketStartedAt,
                Math.toIntExact(window.getSeconds()));
        return count == null ? 0 : count;
    }
}
