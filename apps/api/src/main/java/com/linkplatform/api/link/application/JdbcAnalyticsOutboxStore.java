package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcAnalyticsOutboxStore implements AnalyticsOutboxStore {

    private static final String REDIRECT_CLICK_EVENT_TYPE = "redirect-click";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public JdbcAnalyticsOutboxStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        Gauge.builder("link.analytics.outbox.unpublished", this, JdbcAnalyticsOutboxStore::countUnpublished)
                .description("Number of unpublished analytics outbox records")
                .register(meterRegistry);
        Gauge.builder("link.analytics.outbox.eligible", this, JdbcAnalyticsOutboxStore::countEligible)
                .description("Number of eligible analytics outbox records awaiting delivery")
                .register(meterRegistry);
        Gauge.builder("link.analytics.outbox.parked", this, JdbcAnalyticsOutboxStore::countParked)
                .description("Number of parked analytics outbox records")
                .register(meterRegistry);
        Gauge.builder("link.analytics.outbox.oldest.eligible.age.seconds", this,
                        store -> {
                            OffsetDateTime oldest = store.findOldestEligibleAt();
                            return oldest == null ? 0.0 : (double) java.time.Duration.between(oldest, OffsetDateTime.now(clock)).toSeconds();
                        })
                .description("Age in seconds of the oldest eligible analytics outbox record")
                .register(meterRegistry);
    }

    @Override
    public void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json)
                VALUES (?, ?, ?, ?)
                """,
                redirectClickAnalyticsEvent.eventId(),
                REDIRECT_CLICK_EVENT_TYPE,
                redirectClickAnalyticsEvent.eventKey(),
                serialize(redirectClickAnalyticsEvent));
    }

    @Override
    public List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) {
        return transactionTemplate.execute(status -> {
            List<ClaimCandidate> candidates = jdbcTemplate.query(
                    """
                    SELECT id, claimed_by, claimed_until
                    FROM analytics_outbox
                    WHERE published_at IS NULL
                      AND parked_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """,
                    (resultSet, rowNum) -> new ClaimCandidate(
                            resultSet.getLong("id"),
                            resultSet.getString("claimed_by"),
                            resultSet.getObject("claimed_until", OffsetDateTime.class)),
                    now,
                    now,
                    limit);
            if (candidates.isEmpty()) {
                return List.of();
            }

            List<Long> ids = candidates.stream().map(ClaimCandidate::id).toList();
            String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));

            List<Object> updateParameters = new ArrayList<>();
            updateParameters.add(workerId);
            updateParameters.add(claimedUntil);
            for (ClaimCandidate candidate : candidates) {
                updateParameters.add(candidate.id());
                updateParameters.add(reclaimSummary(workerId, candidate));
            }
            updateParameters.addAll(ids);
            jdbcTemplate.update(
                    """
                    UPDATE analytics_outbox
                    SET claimed_by = ?,
                        claimed_until = ?,
                        last_error_summary = CASE id
                            %s
                            ELSE last_error_summary
                        END
                    WHERE id IN (%s)
                    """.formatted(caseClauses(candidates.size()), placeholders),
                    updateParameters.toArray());

            return jdbcTemplate.query(
                    """
                    SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                           claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                    FROM analytics_outbox
                    WHERE id IN (%s)
                    ORDER BY created_at ASC, id ASC
                    """.formatted(placeholders),
                    (resultSet, rowNum) -> mapRecord(resultSet),
                    ids.toArray());
        });
    }

    @Override
    public long countUnpublished() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countEligible() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM analytics_outbox
                WHERE published_at IS NULL
                  AND parked_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                  AND (claimed_until IS NULL OR claimed_until < ?)
                """,
                Long.class,
                now,
                now);
        return count == null ? 0L : count;
    }

    @Override
    public long countParked() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE parked_at IS NOT NULL AND published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public OffsetDateTime findOldestEligibleAt() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return jdbcTemplate.query(
                        """
                        SELECT created_at
                        FROM analytics_outbox
                        WHERE published_at IS NULL
                          AND parked_at IS NULL
                          AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                          AND (claimed_until IS NULL OR claimed_until < ?)
                        ORDER BY created_at ASC, id ASC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("created_at", OffsetDateTime.class),
                        now,
                        now)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public OffsetDateTime findOldestParkedAt() {
        return jdbcTemplate.query(
                        """
                        SELECT parked_at
                        FROM analytics_outbox
                        WHERE parked_at IS NOT NULL
                          AND published_at IS NULL
                        ORDER BY parked_at ASC, id ASC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("parked_at", OffsetDateTime.class))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void markPublished(long id, OffsetDateTime publishedAt) {
        jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET published_at = ?,
                    claimed_by = NULL,
                    claimed_until = NULL,
                    next_attempt_at = NULL,
                    last_error_summary = NULL,
                    parked_at = NULL
                WHERE id = ?
                  AND published_at IS NULL
                """,
                publishedAt,
                id);
    }

    @Override
    public void recordPublishFailure(
            long id,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET attempt_count = ?,
                    next_attempt_at = ?,
                    last_error_summary = ?,
                    parked_at = ?,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = ?
                  AND published_at IS NULL
                """,
                attemptCount,
                nextAttemptAt,
                lastErrorSummary,
                parkedAt,
                id);
    }

    @Override
    public List<AnalyticsOutboxRecord> findParked(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                       claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                FROM analytics_outbox
                WHERE parked_at IS NOT NULL
                  AND published_at IS NULL
                ORDER BY parked_at DESC, id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                limit);
    }

    @Override
    public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) {
        return jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET parked_at = NULL,
                    next_attempt_at = ?,
                    last_error_summary = NULL,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = ?
                  AND parked_at IS NOT NULL
                  AND published_at IS NULL
                """,
                nextAttemptAt,
                id) == 1;
    }

    @Override
    public int requeueParkedBatch(List<Long> ids, OffsetDateTime nextAttemptAt) {
        if (ids.isEmpty()) {
            return 0;
        }
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<Object> parameters = new ArrayList<>();
        parameters.add(nextAttemptAt);
        parameters.addAll(ids);
        return jdbcTemplate.update(
                """
                UPDATE analytics_outbox
                SET parked_at = NULL,
                    next_attempt_at = ?,
                    last_error_summary = NULL,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE parked_at IS NOT NULL
                  AND published_at IS NULL
                  AND id IN (%s)
                """.formatted(placeholders),
                parameters.toArray());
    }

    @Override
    public int requeueAllParked(int limit) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM analytics_outbox
                WHERE parked_at IS NOT NULL
                  AND published_at IS NULL
                ORDER BY parked_at ASC, id ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                limit);
        return requeueParkedBatch(ids, OffsetDateTime.now(clock));
    }

    @Override
    public long archivePublishedBefore(OffsetDateTime cutoff, int limit) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM analytics_outbox
                    WHERE published_at IS NOT NULL
                      AND published_at < ?
                    ORDER BY published_at ASC, id ASC
                    LIMIT ?
                    """,
                    (resultSet, rowNum) -> resultSet.getLong("id"),
                    cutoff,
                    limit);
            if (ids.isEmpty()) {
                return 0L;
            }

            String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
            List<Object> insertParameters = new ArrayList<>();
            insertParameters.add(OffsetDateTime.now(clock));
            insertParameters.addAll(ids);
            jdbcTemplate.update(
                    """
                    INSERT INTO analytics_outbox_archive (
                        original_id, event_id, event_type, event_key, payload_json, created_at, published_at,
                        attempt_count, next_attempt_at, last_error_summary, parked_at, archived_at
                    )
                    SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                           attempt_count, next_attempt_at, last_error_summary, parked_at, ?
                    FROM analytics_outbox
                    WHERE id IN (%s)
                    """.formatted(placeholders),
                    insertParameters.toArray());

            jdbcTemplate.update(
                    """
                    DELETE FROM analytics_outbox
                    WHERE id IN (%s)
                    """.formatted(placeholders),
                    ids.toArray());
            return (long) ids.size();
        });
    }

    @Override
    public long countArchived() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_outbox_archive", Long.class);
        return count == null ? 0L : count;
    }

    private AnalyticsOutboxRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new AnalyticsOutboxRecord(
                resultSet.getLong("id"),
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getString("event_key"),
                resultSet.getString("payload_json"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getString("claimed_by"),
                resultSet.getObject("claimed_until", OffsetDateTime.class),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("next_attempt_at", OffsetDateTime.class),
                resultSet.getString("last_error_summary"),
                resultSet.getObject("parked_at", OffsetDateTime.class));
    }

    private String caseClauses(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> "WHEN ? THEN COALESCE(?, last_error_summary)")
                .collect(Collectors.joining(" "));
    }

    private String reclaimSummary(String workerId, ClaimCandidate candidate) {
        if (candidate.claimedBy() == null || candidate.claimedUntil() == null) {
            return null;
        }
        String summary = "lease-expired: previous_claimed_by=" + candidate.claimedBy()
                + ", previous_claimed_until=" + candidate.claimedUntil()
                + ", reclaimed_by=" + workerId;
        return summary.length() <= 255 ? summary : summary.substring(0, 255);
    }

    private String serialize(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
        try {
            return objectMapper.writeValueAsString(redirectClickAnalyticsEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analytics outbox payload could not be serialized", exception);
        }
    }

    private record ClaimCandidate(long id, String claimedBy, OffsetDateTime claimedUntil) {
    }
}
