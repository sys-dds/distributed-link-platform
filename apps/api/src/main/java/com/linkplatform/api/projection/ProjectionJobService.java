package com.linkplatform.api.projection;

import com.linkplatform.api.link.application.LinkActivityEvent;
import com.linkplatform.api.link.application.LinkActivityType;
import com.linkplatform.api.link.application.LinkClickHistoryRecord;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleHistoryRecord;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.LinkReadCache;
import com.linkplatform.api.link.application.LinkStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionJobService {

    private final ProjectionJobStore projectionJobStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final Clock clock;
    private final int chunkSize;

    public ProjectionJobService(
            ProjectionJobStore projectionJobStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            @Value("${link-platform.projection-jobs.chunk-size}") int chunkSize) {
        this.projectionJobStore = projectionJobStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.clock = Clock.systemUTC();
        this.chunkSize = chunkSize;
    }

    public ProjectionJob createJob(ProjectionJobType jobType) {
        return projectionJobStore.createJob(jobType, OffsetDateTime.now(clock));
    }

    @Transactional
    public ProjectionJobChunkResult executeClaimedJobChunk(ProjectionJob job) {
        ProjectionJobChunkResult result = switch (job.jobType()) {
            case ACTIVITY_FEED_REPLAY -> replayActivityFeedChunk(job);
            case CLICK_ROLLUP_REBUILD -> rebuildClickRollupsChunk(job);
            case LINK_CATALOG_REBUILD -> rebuildCatalogChunk(job);
            case LINK_DISCOVERY_REBUILD -> rebuildDiscoveryChunk(job);
        };
        if (result.completed()) {
            projectionJobStore.markCompleted(job.id(), OffsetDateTime.now(clock), result.processedCount(), result.checkpointId());
        } else {
            projectionJobStore.markProgress(job.id(), result.processedCount(), result.checkpointId());
        }
        return result;
    }

    private ProjectionJobChunkResult replayActivityFeedChunk(ProjectionJob job) {
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(afterId, chunkSize + 1);
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkLifecycleHistoryRecord> historyChunk = limitToChunk(fetchedChunk);
        Set<Long> ownerIds = new HashSet<>();
        for (LinkLifecycleHistoryRecord historyRecord : historyChunk) {
            LinkLifecycleEvent lifecycleEvent = historyRecord.event();
            linkStore.recordActivityIfAbsent(lifecycleEvent.eventId(), toActivityEvent(lifecycleEvent));
            ownerIds.add(lifecycleEvent.ownerId());
        }
        ownerIds.forEach(linkReadCache::invalidateOwnerAnalytics);
        return new ProjectionJobChunkResult(
                completed,
                historyChunk.size(),
                historyChunk.isEmpty() ? job.checkpointId() : historyChunk.getLast().outboxId());
    }

    private ProjectionJobChunkResult rebuildClickRollupsChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            linkStore.findOwnerIdsWithClickHistory().forEach(linkReadCache::invalidateOwnerAnalytics);
            linkStore.resetClickDailyRollups();
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkClickHistoryRecord> fetchedChunk = linkStore.findClickHistoryChunkAfter(afterId, chunkSize + 1);
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkClickHistoryRecord> clickHistoryChunk = limitToChunk(fetchedChunk);
        long processedCount = linkStore.applyClickHistoryChunkToDailyRollups(clickHistoryChunk);
        clickHistoryChunk.stream()
                .map(LinkClickHistoryRecord::slug)
                .distinct()
                .map(linkStore::findOwnerIdBySlug)
                .flatMap(Optional::stream)
                .forEach(linkReadCache::invalidateOwnerAnalytics);
        return new ProjectionJobChunkResult(
                completed,
                processedCount,
                clickHistoryChunk.isEmpty() ? job.checkpointId() : clickHistoryChunk.getLast().clickId());
    }

    private ProjectionJobChunkResult rebuildCatalogChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            linkStore.resetCatalogProjection();
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(afterId, chunkSize + 1);
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkLifecycleHistoryRecord> historyChunk = limitToChunk(fetchedChunk);
        Set<Long> ownerIds = new HashSet<>();
        for (LinkLifecycleHistoryRecord historyRecord : historyChunk) {
            linkStore.projectCatalogEvent(historyRecord.event());
            ownerIds.add(historyRecord.event().ownerId());
        }
        ownerIds.forEach(linkReadCache::invalidateOwnerControlPlane);
        return new ProjectionJobChunkResult(
                completed,
                historyChunk.size(),
                historyChunk.isEmpty() ? job.checkpointId() : historyChunk.getLast().outboxId());
    }

    private ProjectionJobChunkResult rebuildDiscoveryChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            linkStore.resetDiscoveryProjection();
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(afterId, chunkSize + 1);
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkLifecycleHistoryRecord> historyChunk = limitToChunk(fetchedChunk);
        Set<Long> ownerIds = new HashSet<>();
        for (LinkLifecycleHistoryRecord historyRecord : historyChunk) {
            linkStore.projectDiscoveryEvent(historyRecord.event());
            ownerIds.add(historyRecord.event().ownerId());
        }
        ownerIds.forEach(linkReadCache::invalidateOwnerControlPlane);
        return new ProjectionJobChunkResult(
                completed,
                historyChunk.size(),
                historyChunk.isEmpty() ? job.checkpointId() : historyChunk.getLast().outboxId());
    }

    private <T> List<T> limitToChunk(List<T> fetchedChunk) {
        if (fetchedChunk.size() <= chunkSize) {
            return fetchedChunk;
        }
        return fetchedChunk.subList(0, chunkSize);
    }

    private LinkActivityEvent toActivityEvent(LinkLifecycleEvent lifecycleEvent) {
        LinkActivityType type = switch (lifecycleEvent.eventType()) {
            case CREATED -> LinkActivityType.CREATED;
            case UPDATED, EXPIRATION_UPDATED -> LinkActivityType.UPDATED;
            case DELETED -> LinkActivityType.DELETED;
        };
        return new LinkActivityEvent(
                lifecycleEvent.ownerId(),
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
