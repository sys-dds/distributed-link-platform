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
    void validatesProgressFieldsSemantics() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-04-06T10:00:00Z");
        
        // 1. Create a job
        ProjectionJob job = store.createJob(ProjectionJobType.LINK_CATALOG_REBUILD, requestedAt);
        assertThat(job.startedAt()).isNull();
        assertThat(job.lastChunkAt()).isNull();
        assertThat(job.processedItems()).isEqualTo(0L);
        assertThat(job.failedItems()).isEqualTo(0L);
        assertThat(job.lastError()).isNull();

        // 2. Claim job - startedAt should lock in
        String workerId = UUID.randomUUID().toString();
        OffsetDateTime claimTime = OffsetDateTime.parse("2026-04-06T10:05:00Z");
        OffsetDateTime untilTime = claimTime.plusMinutes(5);
        
        Optional<ProjectionJob> claimed = store.claimNext(workerId, claimTime, untilTime);
        assertThat(claimed).isPresent();
        assertThat(claimed.get().startedAt()).isEqualTo(claimTime);

        // 3. Fail job - verify failedItems and lastError
        OffsetDateTime failTime = OffsetDateTime.parse("2026-04-06T10:06:00Z");
        String errorMsg = "Database connection timeout";
        store.markFailed(job.id(), failTime, 0L, errorMsg);
        
        ProjectionJob failedDbJob = store.findById(job.id()).orElseThrow();
        assertThat(failedDbJob.lastError()).isEqualTo(errorMsg);
        assertThat(failedDbJob.failedItems()).isEqualTo(0L);

        // 4. Re-claim - wait, the user said LAST ERROR RULE MUST PERSIST!
        OffsetDateTime reclaimTime = OffsetDateTime.parse("2026-04-06T10:10:00Z");
        OffsetDateTime reclaimUntil = reclaimTime.plusMinutes(5);
        Optional<ProjectionJob> reclaimed = store.claimNext(workerId, reclaimTime, reclaimUntil);
        
        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.get().startedAt()).isEqualTo(claimTime); // startedAt holds original start
        assertThat(reclaimed.get().lastError()).isEqualTo(errorMsg); // lastError should not be cleared

        // 5. Healthy progress - clears lastError, updates processed and lastChunk
        OffsetDateTime progressTime = OffsetDateTime.parse("2026-04-06T10:15:00Z");
        store.markProgress(job.id(), progressTime, 100L, 1L);
        
        ProjectionJob progressDbJob = store.findById(job.id()).orElseThrow();
        assertThat(progressDbJob.lastError()).isNull();
        assertThat(progressDbJob.processedItems()).isEqualTo(100L);
        assertThat(progressDbJob.lastChunkAt()).isEqualTo(progressTime);

        // 6. Complete Job
        OffsetDateTime completeTime = OffsetDateTime.parse("2026-04-06T10:20:00Z");
        store.markCompleted(job.id(), completeTime, 50L, 2L);
        
        ProjectionJob completeDbJob = store.findById(job.id()).orElseThrow();
        assertThat(completeDbJob.lastError()).isNull();
        assertThat(completeDbJob.processedItems()).isEqualTo(150L);
        assertThat(completeDbJob.completedAt()).isEqualTo(completeTime);
    }
}
