package com.linkplatform.api.projection;

import com.linkplatform.api.link.application.LinkActivityEvent;
import com.linkplatform.api.link.application.LinkActivityType;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.LinkStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionJobService {

    private final ProjectionJobStore projectionJobStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final LinkStore linkStore;
    private final Clock clock;

    public ProjectionJobService(
            ProjectionJobStore projectionJobStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkStore linkStore) {
        this.projectionJobStore = projectionJobStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkStore = linkStore;
        this.clock = Clock.systemUTC();
    }

    public ProjectionJob createJob(ProjectionJobType jobType) {
        return projectionJobStore.createJob(jobType, OffsetDateTime.now(clock));
    }

    @Transactional
    public long executeJob(ProjectionJob job) {
        return switch (job.jobType()) {
            case ACTIVITY_FEED_REPLAY -> replayActivityFeed();
            case CLICK_ROLLUP_REBUILD -> linkStore.rebuildClickDailyRollups();
        };
    }

    private long replayActivityFeed() {
        long processedCount = 0L;
        for (LinkLifecycleEvent lifecycleEvent : linkLifecycleOutboxStore.findAllHistory()) {
            linkStore.recordActivityIfAbsent(lifecycleEvent.eventId(), toActivityEvent(lifecycleEvent));
            processedCount++;
        }
        return processedCount;
    }

    private LinkActivityEvent toActivityEvent(LinkLifecycleEvent lifecycleEvent) {
        LinkActivityType type = switch (lifecycleEvent.eventType()) {
            case CREATED -> LinkActivityType.CREATED;
            case UPDATED, EXPIRATION_UPDATED -> LinkActivityType.UPDATED;
            case DELETED -> LinkActivityType.DELETED;
        };
        return new LinkActivityEvent(
                type,
                lifecycleEvent.slug(),
                lifecycleEvent.originalUrl(),
                lifecycleEvent.title(),
                lifecycleEvent.tags(),
                lifecycleEvent.hostname(),
                lifecycleEvent.expiresAt(),
                lifecycleEvent.occurredAt());
    }
}
