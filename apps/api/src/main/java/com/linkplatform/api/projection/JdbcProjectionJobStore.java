package com.linkplatform.api.projection;

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
    }

    @Override
    public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO projection_jobs (job_type, status, requested_at)
                    VALUES (?, ?, ?)
                    """,
                    new String[]{"id"});
            statement.setString(1, jobType.name());
            statement.setString(2, ProjectionJobStatus.QUEUED.name());
            statement.setObject(3, requestedAt);
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
                               processed_count, error_summary, claimed_by, claimed_until
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
                       processed_count, error_summary, claimed_by, claimed_until
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
                               processed_count, error_summary, claimed_by, claimed_until
                        FROM projection_jobs
                        WHERE (status = 'QUEUED'
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
                    jdbcTemplate.update(
                            """
                            UPDATE projection_jobs
                            SET status = ?,
                                started_at = COALESCE(started_at, ?),
                                completed_at = NULL,
                                error_summary = NULL,
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
    public void markCompleted(long id, OffsetDateTime completedAt, long processedCount) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    completed_at = ?,
                    processed_count = ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    error_summary = NULL
                WHERE id = ?
                """,
                ProjectionJobStatus.COMPLETED.name(),
                completedAt,
                processedCount,
                id);
    }

    @Override
    public void markFailed(long id, OffsetDateTime completedAt, String errorSummary) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    completed_at = ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    error_summary = ?
                WHERE id = ?
                """,
                ProjectionJobStatus.FAILED.name(),
                completedAt,
                errorSummary,
                id);
    }

    @Override
    public long countQueued() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_jobs WHERE status = 'QUEUED'",
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

    private ProjectionJob mapJob(ResultSet resultSet) throws SQLException {
        return new ProjectionJob(
                resultSet.getLong("id"),
                ProjectionJobType.valueOf(resultSet.getString("job_type")),
                ProjectionJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getLong("processed_count"),
                resultSet.getString("error_summary"),
                resultSet.getString("claimed_by"),
                resultSet.getObject("claimed_until", OffsetDateTime.class));
    }
}
