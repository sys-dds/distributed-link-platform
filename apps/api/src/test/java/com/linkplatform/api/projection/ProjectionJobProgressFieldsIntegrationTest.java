package com.linkplatform.api.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProjectionJobProgressFieldsIntegrationTest {

    @Autowired
    private ProjectionJobStore store;

    @Test
    void persistsStartedChunkProcessedFailedAndErrorFieldsWithStableSemantics() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-04-06T10:00:00Z");
        ProjectionJob job = store.createJob(ProjectionJobType.LINK_CATALOG_REBUILD, requestedAt);
        assertThat(job.startedAt()).isNull();
        assertThat(job.lastChunkAt()).isNull();
        assertThat(job.processedItems()).isEqualTo(0L);
        assertThat(job.failedItems()).isEqualTo(0L);
        assertThat(job.lastError()).isNull();

        String workerId = UUID.randomUUID().toString();
        OffsetDateTime claimTime = OffsetDateTime.parse("2026-04-06T10:05:00Z");
        OffsetDateTime untilTime = claimTime.plusMinutes(5);
        Optional<ProjectionJob> claimed = store.claimNext(workerId, claimTime, untilTime);
        assertThat(claimed).isPresent();
        assertThat(claimed.orElseThrow().startedAt()).isNull();
        assertThat(claimed.orElseThrow().lastError()).isNull();

        OffsetDateTime failTime = OffsetDateTime.parse("2026-04-06T10:06:00Z");
        String errorMsg = "Database connection timeout";
        store.markFailed(job.id(), failTime, 1L, errorMsg);

        ProjectionJob failedDbJob = store.findById(job.id()).orElseThrow();
        assertThat(failedDbJob.startedAt()).isEqualTo(failTime);
        assertThat(failedDbJob.lastChunkAt()).isNull();
        assertThat(failedDbJob.lastError()).isEqualTo(errorMsg);
        assertThat(failedDbJob.errorSummary()).isEqualTo(errorMsg);
        assertThat(failedDbJob.failedItems()).isEqualTo(1L);

        OffsetDateTime reclaimTime = OffsetDateTime.parse("2026-04-06T10:10:00Z");
        OffsetDateTime reclaimUntil = reclaimTime.plusMinutes(5);
        Optional<ProjectionJob> reclaimed = store.claimNext(workerId, reclaimTime, reclaimUntil);
        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.orElseThrow().startedAt()).isEqualTo(failTime);
        assertThat(reclaimed.orElseThrow().lastError()).isEqualTo(errorMsg);
        assertThat(reclaimed.orElseThrow().errorSummary()).isEqualTo(errorMsg);

        OffsetDateTime progressTime = OffsetDateTime.parse("2026-04-06T10:15:00Z");
        store.markProgress(job.id(), progressTime, 100L, 1L);
        ProjectionJob progressDbJob = store.findById(job.id()).orElseThrow();
        assertThat(progressDbJob.startedAt()).isEqualTo(failTime);
        assertThat(progressDbJob.lastError()).isNull();
        assertThat(progressDbJob.errorSummary()).isNull();
        assertThat(progressDbJob.processedItems()).isEqualTo(100L);
        assertThat(progressDbJob.lastChunkAt()).isEqualTo(progressTime);

        OffsetDateTime completeTime = OffsetDateTime.parse("2026-04-06T10:20:00Z");
        store.markCompleted(job.id(), completeTime, 50L, 2L);
        ProjectionJob completeDbJob = store.findById(job.id()).orElseThrow();
        assertThat(completeDbJob.startedAt()).isEqualTo(failTime);
        assertThat(completeDbJob.lastError()).isNull();
        assertThat(completeDbJob.errorSummary()).isNull();
        assertThat(completeDbJob.processedItems()).isEqualTo(150L);
        assertThat(completeDbJob.failedItems()).isEqualTo(1L);
        assertThat(completeDbJob.lastChunkAt()).isEqualTo(completeTime);
        assertThat(completeDbJob.completedAt()).isEqualTo(completeTime);
    }

    @Test
    void genericFailurePathsLeaveFailedItemsAtZeroWhenExactItemCountIsUnknown() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-04-06T12:00:00Z");

        ProjectionJob explicitUnknownCountJob = store.createJob(ProjectionJobType.LINK_CATALOG_REBUILD, requestedAt);
        OffsetDateTime explicitFailedAt = OffsetDateTime.parse("2026-04-06T12:05:00Z");
        store.markFailed(explicitUnknownCountJob.id(), explicitFailedAt, 0L, "chunk crashed");

        ProjectionJob explicitUnknownCount = store.findById(explicitUnknownCountJob.id()).orElseThrow();
        assertThat(explicitUnknownCount.failedItems()).isEqualTo(0L);
        assertThat(explicitUnknownCount.lastError()).isEqualTo("chunk crashed");
        assertThat(explicitUnknownCount.errorSummary()).isEqualTo("chunk crashed");

        ProjectionJob defaultUnknownCountJob = store.createJob(
                ProjectionJobType.LINK_CATALOG_REBUILD,
                requestedAt.plusMinutes(1));
        OffsetDateTime defaultFailedAt = OffsetDateTime.parse("2026-04-06T12:06:00Z");
        store.markFailed(defaultUnknownCountJob.id(), defaultFailedAt, "runtime crashed");

        ProjectionJob defaultUnknownCount = store.findById(defaultUnknownCountJob.id()).orElseThrow();
        assertThat(defaultUnknownCount.failedItems()).isEqualTo(0L);
        assertThat(defaultUnknownCount.lastError()).isEqualTo("runtime crashed");
        assertThat(defaultUnknownCount.errorSummary()).isEqualTo("runtime crashed");
    }
}
