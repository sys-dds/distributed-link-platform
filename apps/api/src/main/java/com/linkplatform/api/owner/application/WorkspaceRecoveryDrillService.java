package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceRecoveryDrillService {

    private static final int DEFAULT_LIMIT = 50;

    private final WorkspaceRecoveryDrillStore recoveryDrillStore;
    private final WorkspaceExportService workspaceExportService;
    private final WorkspaceImportService workspaceImportService;
    private final WorkspacePermissionService workspacePermissionService;
    private final OperatorActionLogStore operatorActionLogStore;
    private final SecurityEventStore securityEventStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WorkspaceRecoveryDrillService(
            WorkspaceRecoveryDrillStore recoveryDrillStore,
            WorkspaceExportService workspaceExportService,
            WorkspaceImportService workspaceImportService,
            WorkspacePermissionService workspacePermissionService,
            OperatorActionLogStore operatorActionLogStore,
            SecurityEventStore securityEventStore,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.recoveryDrillStore = recoveryDrillStore;
        this.workspaceExportService = workspaceExportService;
        this.workspaceImportService = workspaceImportService;
        this.workspacePermissionService = workspacePermissionService;
        this.operatorActionLogStore = operatorActionLogStore;
        this.securityEventStore = securityEventStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceRecoveryDrillRecord> list(WorkspaceAccessContext context) {
        workspacePermissionService.requireExportsRead(context);
        return recoveryDrillStore.findByWorkspaceId(context.workspaceId(), DEFAULT_LIMIT);
    }

    @Transactional(readOnly = true)
    public WorkspaceRecoveryDrillRecord get(WorkspaceAccessContext context, long drillId) {
        workspacePermissionService.requireExportsRead(context);
        return recoveryDrillStore.findById(context.workspaceId(), drillId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace recovery drill not found: " + drillId));
    }

    @Transactional
    public WorkspaceRecoveryDrillRecord request(
            WorkspaceAccessContext context,
            long sourceExportId,
            String targetMode,
            Boolean dryRun) {
        workspacePermissionService.requireExportsWrite(context);
        String normalizedMode = normalizeMode(targetMode);
        workspaceExportService.resolveRecoveryDrillPayload(context, sourceExportId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceRecoveryDrillRecord record = recoveryDrillStore.create(
                context.workspaceId(),
                context.ownerId(),
                sourceExportId,
                dryRun == null || dryRun,
                normalizedMode,
                now);
        operatorActionLogStore.recordWorkspaceRecoveryDrill(
                context.workspaceId(),
                context.ownerId(),
                "Workspace recovery drill requested",
                now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_RECOVERY_DRILL_REQUESTED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/recovery-drills",
                null,
                "Workspace recovery drill requested",
                now);
        return record;
    }

    @Transactional
    public void processQueuedDrill(WorkspaceRecoveryDrillRecord record) {
        try {
            WorkspaceAccessContext context = new WorkspaceAccessContext(
                    new AuthenticatedOwner(record.requestedByOwnerId(), "system", "System", OwnerPlan.FREE),
                    record.workspaceId(),
                    null,
                    null,
                    false,
                    WorkspaceRole.OWNER,
                    java.util.Set.of(ApiKeyScope.EXPORTS_READ, ApiKeyScope.EXPORTS_WRITE),
                    null);
            JsonNode payload = workspaceExportService.resolveRecoveryDrillPayload(context, record.sourceExportId());
            JsonNode summary = switch (record.targetMode()) {
                case "SHADOW_VALIDATE" -> shadowValidate(payload);
                case "EMPTY_WORKSPACE_RESTORE" -> emptyWorkspaceRestore(record.workspaceId(), payload);
                case "CONFLICT_ANALYSIS" -> conflictAnalysis(record.workspaceId(), payload);
                default -> throw new IllegalArgumentException("Unsupported recovery drill mode: " + record.targetMode());
            };
            OffsetDateTime now = OffsetDateTime.now(clock);
            recoveryDrillStore.markCompleted(record.id(), summary, now);
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_RECOVERY_DRILL_COMPLETED,
                    record.requestedByOwnerId(),
                    record.workspaceId(),
                    null,
                    "POST",
                    "/api/v1/workspaces/current/recovery-drills/" + record.id(),
                    null,
                    "Workspace recovery drill completed",
                    now);
        } catch (Exception exception) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            recoveryDrillStore.markFailed(record.id(), exception.getMessage(), now);
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_RECOVERY_DRILL_FAILED,
                    record.requestedByOwnerId(),
                    record.workspaceId(),
                    null,
                    "POST",
                    "/api/v1/workspaces/current/recovery-drills/" + record.id(),
                    null,
                    "Workspace recovery drill failed",
                    now);
        }
    }

    private JsonNode shadowValidate(JsonNode payload) {
        ObjectNode summary = baseSummary("SHADOW_VALIDATE", payload);
        ArrayNode issues = summary.putArray("validationIssues");
        try {
            workspaceImportService.validateImportPayload(payload);
        } catch (RuntimeException exception) {
            issues.add(exception.getMessage());
        }
        summary.set("estimatedRestoreCounts", estimatedCounts(payload));
        summary.put("mutatedData", false);
        return summary;
    }

    private JsonNode emptyWorkspaceRestore(long workspaceId, JsonNode payload) {
        long activeLinks = countActiveLinks(workspaceId);
        if (activeLinks > 0) {
            throw new IllegalStateException("EMPTY_WORKSPACE_RESTORE requires an empty target workspace");
        }
        ObjectNode summary = baseSummary("EMPTY_WORKSPACE_RESTORE", payload);
        summary.set("expectedRestoredCounts", estimatedCounts(payload));
        summary.put("mutatedData", false);
        return summary;
    }

    private JsonNode conflictAnalysis(long workspaceId, JsonNode payload) {
        workspaceImportService.validateImportPayload(payload);
        ObjectNode summary = baseSummary("CONFLICT_ANALYSIS", payload);
        ObjectNode counts = objectMapper.createObjectNode();
        int create = 0;
        int update = 0;
        int conflict = 0;
        for (JsonNode link : payload.path("links")) {
            String slug = link.path("slug").asText(null);
            if (slug == null || slug.isBlank()) {
                conflict++;
            } else if (linkExists(workspaceId, slug)) {
                update++;
            } else {
                create++;
            }
        }
        counts.put("create", create);
        counts.put("update", update);
        counts.put("conflict", conflict);
        counts.put("skip", Math.max(0, payload.path("links").size() - create - update - conflict));
        summary.set("counts", counts);
        summary.put("mutatedData", false);
        return summary;
    }

    private ObjectNode baseSummary(String targetMode, JsonNode payload) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("targetMode", targetMode);
        summary.put("links", payload.path("links").size());
        summary.put("webhooks", payload.path("webhooks").size());
        return summary;
    }

    private ObjectNode estimatedCounts(JsonNode payload) {
        ObjectNode counts = objectMapper.createObjectNode();
        counts.put("links", payload.path("links").size());
        counts.put("webhooks", payload.path("webhooks").size());
        return counts;
    }

    private long countActiveLinks(long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM links WHERE workspace_id = ? AND deleted_at IS NULL",
                Long.class,
                workspaceId);
        return count == null ? 0L : count;
    }

    private boolean linkExists(long workspaceId, String slug) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM links WHERE workspace_id = ? AND slug = ?",
                Integer.class,
                workspaceId,
                slug);
        return count != null && count > 0;
    }

    private String normalizeMode(String targetMode) {
        if (targetMode == null || targetMode.isBlank()) {
            throw new IllegalArgumentException("targetMode is required");
        }
        String normalized = targetMode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "SHADOW_VALIDATE", "EMPTY_WORKSPACE_RESTORE", "CONFLICT_ANALYSIS" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported recovery drill mode: " + targetMode);
        };
    }
}
