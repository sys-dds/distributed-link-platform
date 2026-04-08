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
        Gauge.builder("link.projection.jobs.queued", this, JdbcProjectionJobStore::countQueuedForMetrics)
                .description("Number of queued projection jobs")
                .register(meterRegistry);
        Gauge.builder("link.projection.jobs.active", this, JdbcProjectionJobStore::countActiveForMetrics)
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
    public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt) {
        return createJob(jobType, requestedAt, null, null, null, null, null, null, null);
    }

    @Override
    public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug) {
        return createJob(jobType, requestedAt, ownerId, null, slug, null, null, null, null);
    }

    @Override
    public ProjectionJob createJob(
            ProjectionJobType jobType,
            OffsetDateTime requestedAt,
            Long ownerId,
            Long workspaceId,
            String slug,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd,
            Long requestedByOwnerId,
            String operatorNote) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO projection_jobs (
                        job_type, status, requested_at, checkpoint_id, owner_id, workspace_id,
                        slug, range_start, range_end, requested_by_owner_id, operator_note
                    )
                    VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[]{"id"});
            statement.setString(1, jobType.name());
            statement.setString(2, ProjectionJobStatus.QUEUED.name());
            statement.setObject(3, requestedAt);
            statement.setObject(4, ownerId);
            statement.setObject(5, workspaceId);
            statement.setString(6, slug);
            statement.setObject(7, rangeStart);
            statement.setObject(8, rangeEnd);
            statement.setObject(9, requestedByOwnerId);
            statement.setString(10, shorten(operatorNote, 512));
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Projection job id was not generated");
        }
        return jdbcTemplate.query(selectJobsSql() + " WHERE id = ?", this::mapJob, id.longValue()).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<ProjectionJob> findByIdVisibleToWorkspace(long id, long workspaceId, long ownerId, boolean personalWorkspace) {
        if (personalWorkspace) {
            return jdbcTemplate.query(
                            selectJobsSql() + visibilityClause(true) + " AND id = ?",
                            this::mapJob,
                            workspaceId,
                            ownerId,
                            id)
                    .stream()
                    .findFirst();
        }
        return jdbcTemplate.query(
                        selectJobsSql() + visibilityClause(false) + " AND id = ?",
                        this::mapJob,
                        workspaceId,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProjectionJob> findById(long id) {
        throw new UnsupportedOperationException("Legacy unscoped projection job lookup is not supported");
    }

    @Override
    public List<ProjectionJob> findRecent(int limit) {
        throw new UnsupportedOperationException("Legacy unscoped projection job listing is not supported");
    }

    @Override
    public List<ProjectionJob> findRecentVisibleToWorkspace(int limit, long workspaceId, long ownerId, boolean personalWorkspace) {
        if (personalWorkspace) {
            return jdbcTemplate.query(
                    selectJobsSql() + visibilityClause(true) + " ORDER BY requested_at DESC, id DESC LIMIT ?",
                    this::mapJob,
                    workspaceId,
                    ownerId,
                    limit);
        }
        return jdbcTemplate.query(
                selectJobsSql() + visibilityClause(false) + " ORDER BY requested_at DESC, id DESC LIMIT ?",
                this::mapJob,
                workspaceId,
                limit);
    }

    @Override
    public Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return transactionTemplate.execute(status -> jdbcTemplate.query(
                        selectJobsSql() + """
                                WHERE (status = 'QUEUED'
                                       OR status = 'FAILED'
                                       OR (status = 'RUNNING' AND claimed_until IS NOT NULL AND claimed_until < ?))
                                ORDER BY requested_at ASC, id ASC
                                LIMIT 1
                                FOR UPDATE SKIP LOCKED
                                """,
                        this::mapJob,
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
                                completed_at = NULL,
                                claimed_by = ?,
                                claimed_until = ?
                            WHERE id = ?
                            """,
                            ProjectionJobStatus.RUNNING.name(),
                            workerId,
                            claimedUntil,
                            job.id());
                    return jdbcTemplate.query(selectJobsSql() + " WHERE id = ?", this::mapJob, job.id()).stream().findFirst().orElseThrow();
                }));
    }

    @Override
    public Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        throw new UnsupportedOperationException("Legacy queued-only projection job claiming is not supported");
    }

    @Override
    public void markProgress(long id, OffsetDateTime occurredAt, long processedCountIncrement, Long checkpointId) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    started_at = COALESCE(started_at, ?),
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
                occurredAt,
                processedCountIncrement,
                processedCountIncrement,
                checkpointId,
                id);
    }

    @Override
    public void markProgress(long id, long processedCountIncrement, Long checkpointId) {
        markProgress(id, OffsetDateTime.now(clock), processedCountIncrement, checkpointId);
    }

    @Override
    public void markCompleted(long id, OffsetDateTime completedAt, long processedCountIncrement, Long checkpointId) {
        jdbcTemplate.update(
                """
                UPDATE projection_jobs
                SET status = ?,
                    started_at = COALESCE(started_at, ?),
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
                    started_at = COALESCE(started_at, ?),
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
                completedAt,
                failedItemsIncrement,
                shorten(errorSummary, 255),
                shorten(errorSummary, 1024),
                id);
    }

    @Override
    public void markFailed(long id, OffsetDateTime completedAt, String errorSummary) {
        markFailed(id, completedAt, 0L, errorSummary);
    }

    @Override
    public long countQueued() {
        throw new UnsupportedOperationException("Legacy unscoped queued projection job count is not supported");
    }

    @Override
    public long countActive() {
        throw new UnsupportedOperationException("Legacy unscoped active projection job count is not supported");
    }

    @Override
    public long countQueued(long workspaceId) {
        return countByStatusesForWorkspace(workspaceId, "('QUEUED')");
    }

    @Override
    public long countActive(long workspaceId) {
        return countByStatusesForWorkspace(workspaceId, "('RUNNING')");
    }

    @Override
    public long countFailed(long workspaceId) {
        return countByStatusesForWorkspace(workspaceId, "('FAILED')");
    }

    @Override
    public long countCompleted(long workspaceId) {
        return countByStatusesForWorkspace(workspaceId, "('COMPLETED')");
    }

    @Override
    public Optional<OffsetDateTime> findLatestStartedAt(long workspaceId) {
        return findLatestTimestampForWorkspace(workspaceId, "started_at");
    }

    @Override
    public Optional<OffsetDateTime> findLatestFailedAt(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT completed_at
                        FROM projection_jobs
                        WHERE status = 'FAILED'
                          AND workspace_id = ?
                        ORDER BY completed_at DESC, id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("completed_at", OffsetDateTime.class),
                        workspaceId)
                .stream()
                .findFirst();
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

    private double countQueuedForMetrics() {
        return countByStatuses("('QUEUED', 'FAILED')");
    }

    private double countActiveForMetrics() {
        return countByStatuses("('RUNNING')");
    }

    private String selectJobsSql() {
        return """
                SELECT id, job_type, status, requested_at, started_at, completed_at,
                       last_chunk_at, processed_count, processed_items, failed_items, checkpoint_id,
                       error_summary, last_error, claimed_by, claimed_until, owner_id, workspace_id,
                       slug, range_start, range_end, requested_by_owner_id, operator_note
                FROM projection_jobs
                """;
    }

    private String visibilityClause(boolean personalWorkspace) {
        if (personalWorkspace) {
            return """
                    -- Legacy jobs without workspace_id remain visible only inside the matching personal workspace.
                     WHERE (
                         workspace_id = ?
                         OR (
                             workspace_id IS NULL
                             AND COALESCE(requested_by_owner_id, owner_id) = ?
                         )
                     )
                    """;
        }
        return " WHERE workspace_id = ? AND 1 = 1 ";
    }

    private ProjectionJob mapJob(ResultSet resultSet, int rowNum) throws SQLException {
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
                getNullableLong(resultSet, "workspace_id"),
                resultSet.getString("slug"),
                resultSet.getObject("range_start", OffsetDateTime.class),
                resultSet.getObject("range_end", OffsetDateTime.class),
                getNullableLong(resultSet, "requested_by_owner_id"),
                resultSet.getString("operator_note"));
    }

    private long countByStatuses(String statusesSql) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_jobs WHERE status IN " + statusesSql,
                Long.class);
        return count == null ? 0L : count;
    }

    private long countByStatusesForWorkspace(long workspaceId, String statusesSql) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_jobs WHERE status IN " + statusesSql + " AND workspace_id = ?",
                Long.class,
                workspaceId);
        return count == null ? 0L : count;
    }

    private Optional<OffsetDateTime> findLatestTimestampForWorkspace(long workspaceId, String column) {
        return jdbcTemplate.query(
                        """
                        SELECT %s
                        FROM projection_jobs
                        WHERE %s IS NOT NULL
                          AND workspace_id = ?
                        ORDER BY %s DESC, id DESC
                        LIMIT 1
                        """.formatted(column, column, column),
                        (resultSet, rowNum) -> resultSet.getObject(column, OffsetDateTime.class),
                        workspaceId)
                .stream()
                .findFirst();
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
