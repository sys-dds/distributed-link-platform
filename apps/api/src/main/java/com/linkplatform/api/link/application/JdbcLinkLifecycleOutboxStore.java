package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcLinkLifecycleOutboxStore implements LinkLifecycleOutboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public JdbcLinkLifecycleOutboxStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.clock = Clock.systemUTC();
        Gauge.builder("link.lifecycle.outbox.unpublished", this, JdbcLinkLifecycleOutboxStore::countUnpublished)
                .description("Number of unpublished lifecycle outbox records")
                .register(meterRegistry);
        Gauge.builder("link.lifecycle.outbox.eligible", this, JdbcLinkLifecycleOutboxStore::countEligible)
                .description("Number of eligible lifecycle outbox records awaiting delivery")
                .register(meterRegistry);
        Gauge.builder("link.lifecycle.outbox.parked", this, JdbcLinkLifecycleOutboxStore::countParked)
                .description("Number of parked lifecycle outbox records")
                .register(meterRegistry);
        Gauge.builder("link.lifecycle.outbox.oldest.eligible.age.seconds", this,
                        store -> {
                            OffsetDateTime oldest = store.findOldestEligibleAt();
                            return oldest == null ? 0.0 : (double) Duration.between(oldest, OffsetDateTime.now(clock)).toSeconds();
                        })
                .description("Age in seconds of the oldest eligible lifecycle outbox record")
                .register(meterRegistry);
    }

    @Override
    public void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json)
                VALUES (?, ?, ?, ?)
                """,
                linkLifecycleEvent.eventId(),
                linkLifecycleEvent.eventType().name(),
                linkLifecycleEvent.eventKey(),
                serialize(linkLifecycleEvent));
    }

    @Override
    public long countUnpublished() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_lifecycle_outbox WHERE published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countEligible() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM link_lifecycle_outbox
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
                "SELECT COUNT(*) FROM link_lifecycle_outbox WHERE parked_at IS NOT NULL AND published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public OffsetDateTime findOldestEligibleAt() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return jdbcTemplate.query(
                        """
                        SELECT created_at
                        FROM link_lifecycle_outbox
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
                        FROM link_lifecycle_outbox
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
    public List<LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM link_lifecycle_outbox
                    WHERE published_at IS NULL
                      AND parked_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """,
                    (resultSet, rowNum) -> resultSet.getLong("id"),
                    now,
                    now,
                    limit);
            if (ids.isEmpty()) {
                return List.of();
            }

            String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
            List<Object> updateParameters = new ArrayList<>();
            updateParameters.add(workerId);
            updateParameters.add(claimedUntil);
            updateParameters.addAll(ids);
            jdbcTemplate.update(
                    """
                    UPDATE link_lifecycle_outbox
                    SET claimed_by = ?, claimed_until = ?
                    WHERE id IN (%s)
                    """.formatted(placeholders),
                    updateParameters.toArray());

            return jdbcTemplate.query(
                    """
                    SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                           claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                    FROM link_lifecycle_outbox
                    WHERE id IN (%s)
                    ORDER BY created_at ASC, id ASC
                    """.formatted(placeholders),
                    (resultSet, rowNum) -> mapRecord(resultSet),
                    ids.toArray());
        });
    }

    @Override
    public void markPublished(long id, OffsetDateTime publishedAt) {
        jdbcTemplate.update(
                """
                UPDATE link_lifecycle_outbox
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
                UPDATE link_lifecycle_outbox
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
    public List<LinkLifecycleEvent> findAllHistory() {
        return jdbcTemplate.query(
                """
                SELECT payload_json
                FROM link_lifecycle_outbox
                ORDER BY created_at ASC, id ASC
                """,
                (resultSet, rowNum) -> deserialize(resultSet.getString("payload_json")));
    }

    @Override
    public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit) {
        return findHistoryChunkAfter(afterId, limit, null, null);
    }

    @Override
    public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit, Long ownerId, String slug) {
        return findHistoryChunkAfter(afterId, limit, null, ownerId, slug, null, null);
    }

    @Override
    public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(
            long afterId,
            int limit,
            Long workspaceId,
            Long ownerId,
            String slug,
            OffsetDateTime from,
            OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.payload_json
                FROM link_lifecycle_outbox o
                WHERE o.id > ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(afterId);
        if (slug != null && !slug.isBlank()) {
            sql.append(" AND o.event_key = ?");
            parameters.add(slug);
        }
        if (ownerId != null) {
            sql.append(" AND o.payload_json LIKE ?");
            parameters.add("%\"ownerId\":" + ownerId + "%");
        }
        if (workspaceId != null) {
            sql.append(" AND o.payload_json LIKE ?");
            parameters.add("%\"workspaceId\":" + workspaceId + "%");
        }
        if (from != null) {
            sql.append(" AND o.created_at >= ?");
            parameters.add(from);
        }
        if (to != null) {
            sql.append(" AND o.created_at < ?");
            parameters.add(to);
        }
        sql.append("""
                ORDER BY o.id ASC
                LIMIT ?
                """);
        parameters.add(limit);
        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new LinkLifecycleHistoryRecord(
                        resultSet.getLong("id"),
                        deserialize(resultSet.getString("payload_json"))),
                parameters.toArray());
    }

    @Override
    public List<LinkLifecycleOutboxRecord> findParked(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                       claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                FROM link_lifecycle_outbox
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
                UPDATE link_lifecycle_outbox
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
                UPDATE link_lifecycle_outbox
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
                FROM link_lifecycle_outbox
                WHERE parked_at IS NOT NULL
                  AND published_at IS NULL
                ORDER BY parked_at ASC, id ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                limit);
        return requeueParkedBatch(ids, OffsetDateTime.now(clock));
    }

    private LinkLifecycleOutboxRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new LinkLifecycleOutboxRecord(
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

    private String serialize(LinkLifecycleEvent linkLifecycleEvent) {
        try {
            return objectMapper.writeValueAsString(linkLifecycleEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle outbox payload could not be serialized", exception);
        }
    }

    private LinkLifecycleEvent deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, LinkLifecycleEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle outbox payload could not be deserialized", exception);
        }
    }
}
