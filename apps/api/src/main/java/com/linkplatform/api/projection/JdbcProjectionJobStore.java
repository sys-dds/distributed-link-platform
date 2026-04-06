package com.linkplatform.api.projection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcProjectionJobStore implements ProjectionJobStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Counter reclaimedCounter;

    public JdbcProjectionJobStore(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = Clock.systemUTC();
        Gauge.builder("link.projection.jobs.queued", this, JdbcProjectionJobStore::countQueued)
                .description("Number of queued projection jobs")
                .register(meterRegistry);
        Gauge.builder("link.projection.jobs.active", this, JdbcProjectionJobStore::countActive)
                .description("Number of active projection jobs")
                .register(meterRegistry);
        Gauge.builder("link.projection.jobs.oldest.queued.age.seconds", this,
                        store -> store.findOldestQueuedAgeSeconds(OffsetDateTime.now(clock)))
                .description("Age in seconds of the oldest queued or retryable projection job")
                .register(meterRegistry);
        this.reclaimedCounter = Counter.builder("link.projection.jobs.reclaimed")
                .description("Number of projection jobs reclaimed after failure or stale lease")
                .register(meterRegistry);
    }

    @Override
    public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO projection_jobs (job_type, status, requested_at, checkpoint_id, owner_id, slug)
                    VALUES (?, ?, ?, NULL, ?, ?)
                    """,
                    new String[]{"id"});
            statement.setString(1, jobType.name());
            statement.setString(2, ProjectionJobStatus.QUEUED.name());
            statement.setObject(3, requestedAt);
            statement.setObject(4, ownerId);
            statement.setString(5, slug);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Projection job id was not generated");
        }
        return findById(id.longValue()).orElseThrow();
    }

    @Override
    public Optional<ProjectionJob> findById(long id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, job_type, status, requested_at, started_at, completed_at,
                               last_chunk_at, processed_count, processed_items, failed_items, checkpoint_id,
                               error_summary, last_error, claimed_by, claimed_until, owner_id, slug
                        FROM projection_jobs
                        WHERE id = ?
                        """,
                        (resultSet, rowNum) -> mapJob(resultSet),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<ProjectionJob> findRecent(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, job_type, status, requested_at, started_at, completed_at,
                       last_chunk_at, processed_count, processed_items, failed_items, checkpoint_id,
                       error_summary, last_error, claimed_by, claimed_until, owner_id, slug
                FROM projection_jobs
                ORDER BY requested_at DESC, id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> mapJob(resultSet),
                limit);
    }

    @Override
    public Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return transactionTemplate.execute(status -> jdbcTemplate.query(
                        """
                        SELECT id, job_type, status, requested_at, started_at, completed_at,
                               last_chunk_at, processed_count, processed_items, failed_items, checkpoint_id,
                               error_summary, last_error, claimed_by, claimed_until, owner_id, slug
                        FROM projection_jobs
                        WHERE (status = 'QUEUED'
                               OR status = 'FAILED'
                               OR (status = 'RUNNING' AND claimed_until IS NOT NULL AND claimed_until < ?))
                        ORDER BY requested_at ASC, id ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """,
                        (resultSet, rowNum) -> mapJob(resultSet),
                        now)
                .stream()
                .findFirst()
                .map(job -> {
                    if (job.status() == ProjectionJobStatus.FAILED || job.status() == ProjectionJobStatus.RUNNING) {
                        reclaimedCounter.increment();
                    }
                    jdbcTemplate.update(
                            """
                            UPDATE projection_jobs
                            SET status = ?,
                                started_at = COALESCE(started_at, ?),
                                completed_at = NULL,
                                error_summary = NULL,
                                last_error = NULL,
                                claimed_by = ?,
                                claimed_until = ?
                            WHERE id = ?
                            """,
                            ProjectionJobStatus.RUNNING.name(),
                            now,
                            workerId,
                            claimedUntil,
                            job.id());
                    return findById(job.id()).orElseThrow();
                }));
    }

    @Override
    public void markProgress(long id, OffsetDateTime occurredAt, long processedCountIncrement, Long checkpointId) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    last_chunk_at = ?,
                    processed_count = processed_count + ?,
                    processed_items = processed_items + ?,
                    checkpoint_id = ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    error_summary = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                ProjectionJobStatus.QUEUED.name(),
                occurredAt,
                processedCountIncrement,
                processedCountIncrement,
                checkpointId,
                id);
    }

    @Override
    public void markCompleted(long id, OffsetDateTime completedAt, long processedCountIncrement, Long checkpointId) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    completed_at = ?,
                    last_chunk_at = ?,
                    processed_count = processed_count + ?,
                    processed_items = processed_items + ?,
                    checkpoint_id = ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    error_summary = NULL,
                    last_error = NULL
                WHERE id = ?
                """,
                ProjectionJobStatus.COMPLETED.name(),
                completedAt,
                completedAt,
                processedCountIncrement,
                processedCountIncrement,
                checkpointId,
                id);
    }

    @Override
    public void markFailed(long id, OffsetDateTime completedAt, long failedItemsIncrement, String errorSummary) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    completed_at = ?,
                    failed_items = failed_items + ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    error_summary = ?,
                    last_error = ?
                WHERE id = ?
                """,
                ProjectionJobStatus.FAILED.name(),
                completedAt,
                failedItemsIncrement,
                shorten(errorSummary, 255),
                shorten(errorSummary, 1024),
                id);
    }

    @Override
    public long countQueued() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_jobs WHERE status IN ('QUEUED', 'FAILED')",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countActive() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_jobs WHERE status = 'RUNNING'",
                Long.class);
        return count == null ? 0L : count;
    }

    Double findOldestQueuedAgeSeconds(OffsetDateTime now) {
        OffsetDateTime oldestRequestedAt = jdbcTemplate.query(
                        """
                        SELECT requested_at
                        FROM projection_jobs
                        WHERE status IN ('QUEUED', 'FAILED')
                        ORDER BY requested_at ASC, id ASC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("requested_at", OffsetDateTime.class))
                .stream()
                .findFirst()
                .orElse(null);
        if (oldestRequestedAt == null) {
            return null;
        }
        return (double) java.time.Duration.between(oldestRequestedAt.toInstant(), now.toInstant()).getSeconds();
    }

    private ProjectionJob mapJob(ResultSet resultSet) throws SQLException {
        return new ProjectionJob(
                resultSet.getLong("id"),
                ProjectionJobType.valueOf(resultSet.getString("job_type")),
                ProjectionJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("last_chunk_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getLong("processed_count"),
                resultSet.getLong("processed_items"),
                resultSet.getLong("failed_items"),
                getNullableLong(resultSet, "checkpoint_id"),
                resultSet.getString("error_summary"),
                resultSet.getString("last_error"),
                resultSet.getString("claimed_by"),
                resultSet.getObject("claimed_until", OffsetDateTime.class),
                getNullableLong(resultSet, "owner_id"),
                resultSet.getString("slug"));
    }

    @Override
    public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt) {
        return createJob(jobType, requestedAt, null, null);
    }

    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
