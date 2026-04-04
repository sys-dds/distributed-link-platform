package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class JdbcProjectionJobStoreTest {

    @Autowired
    private JdbcProjectionJobStore store;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void createAndClaimJobUpdatesQueuedAndActiveGauges() {
        ProjectionJob created = store.createJob(
                ProjectionJobType.ACTIVITY_FEED_REPLAY,
                OffsetDateTime.parse("2026-04-04T10:00:00Z"));

        assertEquals(1L, store.countQueued());
        assertEquals(1.0, meterRegistry.get("link.projection.jobs.queued").gauge().value());

        ProjectionJob claimed = store.claimNext(
                        "worker-a",
                        OffsetDateTime.parse("2026-04-04T10:01:00Z"),
                        OffsetDateTime.parse("2026-04-04T10:02:00Z"))
                .orElseThrow();

        assertEquals(created.id(), claimed.id());
        assertEquals(ProjectionJobStatus.RUNNING, claimed.status());
        assertEquals(0L, store.countQueued());
        assertEquals(1L, store.countActive());
        assertEquals(1.0, meterRegistry.get("link.projection.jobs.active").gauge().value());
    }

    @Test
    void concurrentWorkersDoNotClaimSameJob() throws Exception {
        store.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY, OffsetDateTime.parse("2026-04-04T10:00:00Z"));
        store.createJob(ProjectionJobType.CLICK_ROLLUP_REBUILD, OffsetDateTime.parse("2026-04-04T10:00:01Z"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ProjectionJob> first = executor.submit(() -> claimAsync("worker-a", ready, start));
            Future<ProjectionJob> second = executor.submit(() -> claimAsync("worker-b", ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Set<Long> claimedIds = ConcurrentHashMap.newKeySet();
            claimedIds.add(first.get(5, TimeUnit.SECONDS).id());
            claimedIds.add(second.get(5, TimeUnit.SECONDS).id());
            assertEquals(2, claimedIds.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleRunningJobBecomesClaimableAgain() {
        ProjectionJob created = store.createJob(
                ProjectionJobType.ACTIVITY_FEED_REPLAY,
                OffsetDateTime.parse("2026-04-04T10:00:00Z"));
        store.claimNext(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T10:01:00Z"),
                OffsetDateTime.parse("2026-04-04T10:01:30Z"));

        ProjectionJob reclaimed = store.claimNext(
                        "worker-b",
                        OffsetDateTime.parse("2026-04-04T10:02:00Z"),
                        OffsetDateTime.parse("2026-04-04T10:02:30Z"))
                .orElseThrow();

        assertEquals(created.id(), reclaimed.id());
        assertEquals("worker-b", reclaimed.claimedBy());
    }

    @Test
    void failedJobRemainsCountedAsQueuedBacklogAndCanBeReclaimed() {
        ProjectionJob created = store.createJob(
                ProjectionJobType.LINK_CATALOG_REBUILD,
                OffsetDateTime.parse("2026-04-04T10:00:00Z"));
        store.markFailed(created.id(), OffsetDateTime.parse("2026-04-04T10:05:00Z"), "boom");

        assertEquals(1L, store.countQueued());

        ProjectionJob reclaimed = store.claimNext(
                        "worker-c",
                        OffsetDateTime.parse("2026-04-04T10:06:00Z"),
                        OffsetDateTime.parse("2026-04-04T10:07:00Z"))
                .orElseThrow();

        assertEquals(created.id(), reclaimed.id());
        assertEquals(ProjectionJobStatus.RUNNING, reclaimed.status());
    }

    private ProjectionJob claimAsync(String workerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 5; attempt++) {
            java.util.Optional<ProjectionJob> claimed = store.claimNext(
                    workerId,
                    OffsetDateTime.parse("2026-04-04T10:01:00Z"),
                    OffsetDateTime.parse("2026-04-04T10:02:00Z"));
            if (claimed.isPresent()) {
                return claimed.orElseThrow();
            }
            Thread.sleep(50);
        }
        throw new java.util.NoSuchElementException("No projection job could be claimed");
    }
}
