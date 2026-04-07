package com.linkplatform.api.projection;

import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class ProjectionJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectionJobRunner.class);
    private static final long FAILED_ITEMS_INCREMENT = 1L;

    private final ProjectionJobStore projectionJobStore;
    private final ProjectionJobService projectionJobService;
    private final Clock clock;
    private final Duration leaseDuration;
    private final String workerId;
    private final Counter startedCounter;
    private final Counter completedCounter;
    private final Counter failedCounter;
    private final Timer durationTimer;

    @Autowired
    public ProjectionJobRunner(
            ProjectionJobStore projectionJobStore,
            ProjectionJobService projectionJobService,
            MeterRegistry meterRegistry,
            @Value("${link-platform.projection-jobs.lease-duration}") String leaseDuration) {
        this(
                projectionJobStore,
                projectionJobService,
                meterRegistry,
                Duration.parse(leaseDuration),
                Clock.systemUTC(),
                UUID.randomUUID().toString());
    }

    ProjectionJobRunner(
            ProjectionJobStore projectionJobStore,
            ProjectionJobService projectionJobService,
            MeterRegistry meterRegistry,
            Duration leaseDuration,
            Clock clock,
            String workerId) {
        this.projectionJobStore = projectionJobStore;
        this.projectionJobService = projectionJobService;
        this.leaseDuration = leaseDuration;
        this.clock = clock;
        this.workerId = workerId;
        this.startedCounter = Counter.builder("link.projection.jobs.started")
                .description("Number of projection jobs started")
                .register(meterRegistry);
        this.completedCounter = Counter.builder("link.projection.jobs.completed")
                .description("Number of projection jobs completed")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("link.projection.jobs.failed")
                .description("Number of projection jobs failed")
                .register(meterRegistry);
        this.durationTimer = Timer.builder("link.projection.jobs.duration")
                .description("Duration of projection jobs")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${link-platform.projection-jobs.runner-delay}")
    public void runPendingJobs() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<ProjectionJob> claimedJob = projectionJobStore.claimNext(workerId, now, now.plus(leaseDuration));
        if (claimedJob.isEmpty()) {
            return;
        }

        ProjectionJob job = claimedJob.orElseThrow();
        startedCounter.increment();
        Timer.Sample sample = Timer.start();
        try {
            ProjectionJobChunkResult result = projectionJobService.executeClaimedJobChunk(job);
            if (result.completed()) {
                completedCounter.increment();
            }
        } catch (RuntimeException exception) {
            projectionJobStore.markFailed(
                    job.id(),
                    OffsetDateTime.now(clock),
                    FAILED_ITEMS_INCREMENT,
                    compactErrorSummary(exception));
            failedCounter.increment();
            log.warn("projection_job_failed id={} type={} reason={}", job.id(), job.jobType(), compactErrorSummary(exception));
            throw exception;
        } finally {
            sample.stop(durationTimer);
        }
    }

    private String compactErrorSummary(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String summary = root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 1024 ? summary : summary.substring(0, 1024);
    }
}
