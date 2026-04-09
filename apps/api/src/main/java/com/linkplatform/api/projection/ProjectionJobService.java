package com.linkplatform.api.projection;

import com.linkplatform.api.link.application.ClickRollupDriftRecord;
import com.linkplatform.api.link.application.ClickRollupRepairStatus;
import com.linkplatform.api.link.application.LinkActivityEvent;
import com.linkplatform.api.link.application.LinkActivityType;
import com.linkplatform.api.link.application.LinkClickHistoryRecord;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleHistoryRecord;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.LinkReadCache;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionJobService {

    private static final long MAX_RANGE_DAYS = 90;

    private final ProjectionJobStore projectionJobStore;
    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;
    private final int chunkSize;

    public ProjectionJobService(
            ProjectionJobStore projectionJobStore,
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            SecurityEventStore securityEventStore,
            @Value("${link-platform.projection-jobs.chunk-size}") int chunkSize) {
        this.projectionJobStore = projectionJobStore;
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
        this.chunkSize = chunkSize;
    }

    public ProjectionJob createJob(ProjectionJobType jobType) {
        return createJob(jobType, null, null, null, null, null, null, null);
    }

    public ProjectionJob createJob(ProjectionJobType jobType, Long ownerId, String slug) {
        return createJob(jobType, ownerId, null, slug, null, null, null, null);
    }

    public ProjectionJob createJob(
            ProjectionJobType jobType,
            Long ownerId,
            Long workspaceId,
            String slug,
            OffsetDateTime from,
            OffsetDateTime to,
            Long requestedByOwnerId,
            String operatorNote) {
        validateScope(jobType, from, to);
        long requiredWorkspaceId = requireWorkspaceScope(workspaceId);
        return projectionJobStore.createJob(
                jobType,
                OffsetDateTime.now(clock),
                ownerId,
                requiredWorkspaceId,
                blankToNull(slug),
                from,
                to,
                requestedByOwnerId,
                blankToNull(operatorNote));
    }

    @Transactional(readOnly = true)
    public Optional<ProjectionJob> findVisibleJob(long id, long workspaceId, long ownerId, boolean personalWorkspace) {
        return projectionJobStore.findByIdVisibleToWorkspace(id, workspaceId, ownerId, personalWorkspace);
    }

    @Transactional(readOnly = true)
    public List<ProjectionJob> findRecentVisibleJobs(int limit, long workspaceId, long ownerId, boolean personalWorkspace) {
        return projectionJobStore.findRecentVisibleToWorkspace(limit, workspaceId, ownerId, personalWorkspace);
    }

    @Transactional
    public ProjectionJobChunkResult executeClaimedJobChunk(ProjectionJob job) {
        ProjectionJobChunkResult result = switch (job.jobType()) {
            case ACTIVITY_FEED_REPLAY -> replayActivityFeedChunk(job);
            case CLICK_ROLLUP_REBUILD -> rebuildClickRollupsChunk(job);
            case CLICK_ROLLUP_RECONCILE -> reconcileClickRollupsChunk(job);
            case LINK_CATALOG_REBUILD -> rebuildCatalogChunk(job);
            case LINK_DISCOVERY_REBUILD -> rebuildDiscoveryChunk(job);
        };
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        if (result.completed()) {
            projectionJobStore.markCompleted(job.id(), occurredAt, result.processedCount(), result.checkpointId());
        } else {
            projectionJobStore.markProgress(job.id(), occurredAt, result.processedCount(), result.checkpointId());
        }
        return result;
    }

    private ProjectionJobChunkResult replayActivityFeedChunk(ProjectionJob job) {
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(
                afterId,
                chunkSize + 1,
                job.workspaceId(),
                job.ownerId(),
                job.slug(),
                job.rangeStart(),
                job.rangeEnd());
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkLifecycleHistoryRecord> historyChunk = limitToChunk(fetchedChunk);
        Set<Long> ownerIds = new HashSet<>();
        for (LinkLifecycleHistoryRecord historyRecord : historyChunk) {
            LinkLifecycleEvent lifecycleEvent = historyRecord.event();
            linkStore.recordActivityIfAbsent(lifecycleEvent.eventId(), toActivityEvent(lifecycleEvent));
            ownerIds.add(lifecycleEvent.ownerId());
        }
        invalidateOwnerAnalyticsCaches(ownerIds);
        return new ProjectionJobChunkResult(
                completed,
                historyChunk.size(),
                historyChunk.isEmpty() ? job.checkpointId() : Long.valueOf(historyChunk.getLast().outboxId()));
    }

    private ProjectionJobChunkResult rebuildClickRollupsChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            invalidateOwnerAnalyticsCaches(new HashSet<>(linkStore.findOwnerIdsWithClickHistory(
                    job.workspaceId(),
                    job.ownerId(),
                    job.slug(),
                    job.rangeStart(),
                    job.rangeEnd())));
            linkStore.resetClickDailyRollups(job.workspaceId(), job.ownerId(), job.slug(), job.rangeStart(), job.rangeEnd());
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkClickHistoryRecord> fetchedChunk = linkStore.findClickHistoryChunkAfter(
                afterId,
                chunkSize + 1,
                job.workspaceId(),
                job.ownerId(),
                job.slug(),
                job.rangeStart(),
                job.rangeEnd());
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkClickHistoryRecord> clickHistoryChunk = limitToChunk(fetchedChunk);
        long processedCount = linkStore.applyClickHistoryChunkToDailyRollups(clickHistoryChunk);
        clickHistoryChunk.stream()
                .map(LinkClickHistoryRecord::slug)
                .distinct()
                .map(linkStore::findOwnerIdBySlug)
                .flatMap(Optional::stream)
                .forEach(this::invalidateOwnerAnalyticsCaches);
        if (completed) {
            invalidateOwnerAnalyticsCaches(new HashSet<>(linkStore.findOwnerIdsWithClickHistory(
                    job.workspaceId(),
                    job.ownerId(),
                    job.slug(),
                    job.rangeStart(),
                    job.rangeEnd())));
        }
        return new ProjectionJobChunkResult(
                completed,
                processedCount,
                clickHistoryChunk.isEmpty() ? job.checkpointId() : Long.valueOf(clickHistoryChunk.getLast().clickId()));
    }

    private ProjectionJobChunkResult rebuildCatalogChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            linkStore.resetCatalogProjection(job.workspaceId(), job.ownerId(), job.slug());
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(
                afterId,
                chunkSize + 1,
                job.workspaceId(),
                job.ownerId(),
                job.slug(),
                null,
                null);
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
                historyChunk.isEmpty() ? job.checkpointId() : Long.valueOf(historyChunk.getLast().outboxId()));
    }

    private ProjectionJobChunkResult reconcileClickRollupsChunk(ProjectionJob job) {
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkClickHistoryRecord> fetchedChunk = linkStore.findClickHistoryChunkForReconciliationAfter(
                afterId,
                chunkSize + 1,
                job.workspaceId(),
                job.ownerId(),
                job.slug(),
                job.rangeStart(),
                job.rangeEnd());
        boolean completed = fetchedChunk.size() <= chunkSize;
        List<LinkClickHistoryRecord> clickHistoryChunk = limitToChunk(fetchedChunk);
        Map<String, Long> rawCounts = new HashMap<>();
        Map<String, String> slugByKey = new HashMap<>();
        Map<String, LocalDate> bucketDayByKey = new HashMap<>();
        for (LinkClickHistoryRecord record : clickHistoryChunk) {
            String key = record.slug() + "|" + record.rollupDate();
            rawCounts.merge(key, 1L, Long::sum);
            slugByKey.put(key, record.slug());
            bucketDayByKey.put(key, record.rollupDate());
        }
        Map<String, Long> currentRollups = linkStore.findDailyRollupTotalsBySlugAndDay(rawCounts.keySet());
        long driftCount = 0L;
        long repairCount = 0L;
        Set<Long> ownersToInvalidate = new HashSet<>();
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Map.Entry<String, Long> entry : rawCounts.entrySet()) {
            String key = entry.getKey();
            String slug = slugByKey.get(key);
            LocalDate bucketDay = bucketDayByKey.get(key);
            long rawClickCount = entry.getValue();
            long rollupClickCount = currentRollups.getOrDefault(key, 0L);
            if (rawClickCount != rollupClickCount) {
                Long ownerId = linkStore.findOwnerIdBySlug(slug).orElse(null);
                driftCount++;
                linkStore.upsertClickRollupReconciliation(new ClickRollupDriftRecord(
                        ownerId,
                        slug,
                        bucketDay,
                        rawClickCount,
                        rollupClickCount,
                        rawClickCount - rollupClickCount,
                        now,
                        now,
                        ClickRollupRepairStatus.REPAIRED,
                        "Rollup overwritten from raw click history"));
                linkStore.repairDailyRollupTotal(slug, bucketDay, rawClickCount);
                repairCount++;
                if (ownerId != null) {
                    ownersToInvalidate.add(ownerId);
                }
                recordSecurityEvent(SecurityEventType.CLICK_ROLLUP_DRIFT_DETECTED, ownerId, slug, now);
                recordSecurityEvent(SecurityEventType.CLICK_ROLLUP_REPAIRED, ownerId, slug, now);
            }
        }
        invalidateOwnerAnalyticsCaches(ownersToInvalidate);
        return new ProjectionJobChunkResult(
                completed,
                clickHistoryChunk.size(),
                clickHistoryChunk.isEmpty() ? job.checkpointId() : Long.valueOf(clickHistoryChunk.getLast().clickId()),
                driftCount,
                repairCount);
    }

    private ProjectionJobChunkResult rebuildDiscoveryChunk(ProjectionJob job) {
        if (job.checkpointId() == null) {
            linkStore.resetDiscoveryProjection(job.workspaceId(), job.ownerId(), job.slug());
        }
        long afterId = job.checkpointId() == null ? 0L : job.checkpointId();
        List<LinkLifecycleHistoryRecord> fetchedChunk = linkLifecycleOutboxStore.findHistoryChunkAfter(
                afterId,
                chunkSize + 1,
                job.workspaceId(),
                job.ownerId(),
                job.slug(),
                null,
                null);
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
                historyChunk.isEmpty() ? job.checkpointId() : Long.valueOf(historyChunk.getLast().outboxId()));
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
            case UPDATED, RESTORED, EXPIRED, EXPIRATION_UPDATED, SUSPENDED, RESUMED, ARCHIVED, UNARCHIVED -> LinkActivityType.UPDATED;
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

    private void invalidateOwnerAnalyticsCaches(Long ownerId) {
        if (ownerId == null) {
            return;
        }
        linkReadCache.invalidateOwnerControlPlane(ownerId);
        linkReadCache.invalidateOwnerAnalytics(ownerId);
    }

    private void invalidateOwnerAnalyticsCaches(Set<Long> ownerIds) {
        ownerIds.forEach(this::invalidateOwnerAnalyticsCaches);
    }

    private void recordSecurityEvent(SecurityEventType eventType, Long ownerId, String slug, OffsetDateTime occurredAt) {
        securityEventStore.record(
                eventType,
                ownerId,
                null,
                "POST",
                "/api/v1/projection-jobs",
                null,
                "Click rollup reconciliation for " + slug,
                occurredAt);
    }

    private void validateScope(ProjectionJobType jobType, OffsetDateTime from, OffsetDateTime to) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("from and to must be provided together");
        }
        if (from != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be strictly before to");
        }
        if (from != null && Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new IllegalArgumentException("Maximum range is 90 days");
        }
        if (from != null && (jobType == ProjectionJobType.LINK_CATALOG_REBUILD || jobType == ProjectionJobType.LINK_DISCOVERY_REBUILD)) {
            throw new IllegalArgumentException("from/to is not supported for " + jobType.name());
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long requireWorkspaceScope(Long workspaceId) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspace scope is required");
        }
        return workspaceId;
    }
}
