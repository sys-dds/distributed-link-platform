package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcControlPlaneRateLimitStore implements ControlPlaneRateLimitStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcControlPlaneRateLimitStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryConsume(long ownerId, ControlPlaneRateLimitBucket bucket, OffsetDateTime windowStartedAt, int limit) {
        int updated = jdbcTemplate.update(
                """
                UPDATE owner_control_plane_rate_limits
                SET request_count = request_count + 1
                WHERE owner_id = ?
                  AND rate_limit_bucket = ?
                  AND window_started_at = ?
                  AND request_count < ?
                """,
                ownerId,
                bucket.name(),
                windowStartedAt,
                limit);
        if (updated == 1) {
            return true;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO owner_control_plane_rate_limits (
                        owner_id, rate_limit_bucket, window_started_at, request_count
                    ) VALUES (?, ?, ?, 1)
                    """,
                    ownerId,
                    bucket.name(),
                    windowStartedAt);
            return true;
        } catch (DuplicateKeyException exception) {
            return jdbcTemplate.update(
                    """
                    UPDATE owner_control_plane_rate_limits
                    SET request_count = request_count + 1
                    WHERE owner_id = ?
                      AND rate_limit_bucket = ?
                      AND window_started_at = ?
                      AND request_count < ?
                    """,
                    ownerId,
                    bucket.name(),
                    windowStartedAt,
                    limit) == 1;
        }
    }
}
