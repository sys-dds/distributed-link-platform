package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceExportService {

    private final WorkspaceExportStore workspaceExportStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final WorkspaceStore workspaceStore;
    private final JdbcTemplate jdbcTemplate;
    private final OperatorActionLogStore operatorActionLogStore;
    private final SecurityEventStore securityEventStore;
    private final ObjectMapper objectMapper;
    private final com.linkplatform.api.runtime.LinkPlatformRuntimeProperties runtimeProperties;
    private final Clock clock;

    public WorkspaceExportService(
            WorkspaceExportStore workspaceExportStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            WorkspaceStore workspaceStore,
            JdbcTemplate jdbcTemplate,
            OperatorActionLogStore operatorActionLogStore,
            SecurityEventStore securityEventStore,
            ObjectMapper objectMapper,
            com.linkplatform.api.runtime.LinkPlatformRuntimeProperties runtimeProperties,
            Clock clock) {
        this.workspaceExportStore = workspaceExportStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspaceStore = workspaceStore;
        this.jdbcTemplate = jdbcTemplate;
        this.operatorActionLogStore = operatorActionLogStore;
        this.securityEventStore = securityEventStore;
        this.objectMapper = objectMapper;
        this.runtimeProperties = runtimeProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkspaceExportRecord> list(WorkspaceAccessContext context) {
        return workspaceExportStore.findByWorkspaceId(context.workspaceId(), 50);
    }

    @Transactional
    public WorkspaceExportRecord requestExport(
            WorkspaceAccessContext context,
            boolean includeClicks,
            boolean includeSecurityEvents,
            boolean includeAbuseCases,
            boolean includeWebhooks) {
        if (!workspaceEntitlementService.exportsEnabled(context.workspaceId())) {
            throw new IllegalArgumentException("Workspace exports are not enabled for this plan");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceExportRecord record = workspaceExportStore.create(
                context.workspaceId(),
                context.ownerId(),
                includeClicks,
                includeSecurityEvents,
                includeAbuseCases,
                includeWebhooks,
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "workspace_export_create",
                null,
                null,
                null,
                "Workspace export requested",
                now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_EXPORT_REQUESTED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/exports",
                null,
                "Workspace export requested",
                now);
        return record;
    }

    @Transactional(readOnly = true)
    public WorkspaceExportRecord get(WorkspaceAccessContext context, long exportId) {
        return workspaceExportStore.findById(context.workspaceId(), exportId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace export not found: " + exportId));
    }

    @Transactional(readOnly = true)
    public JsonNode resolveImportPayload(WorkspaceAccessContext context, long exportId) {
        WorkspaceExportRecord exportRecord = workspaceExportStore.findCompletedById(context.workspaceId(), exportId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace export is not ready: " + exportId));
        if (exportRecord.payload() == null) {
            throw new IllegalArgumentException("Workspace export payload is not available");
        }
        return exportRecord.payload();
    }

    @Transactional(readOnly = true)
    public JsonNode resolveRecoveryDrillPayload(WorkspaceAccessContext context, long exportId) {
        WorkspaceExportRecord exportRecord = workspaceExportStore.findRecoveryDrillSourceById(context.workspaceId(), exportId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace export is not ready: " + exportId));
        if (exportRecord.payload() == null) {
            throw new IllegalArgumentException("Workspace export payload is not available");
        }
        return exportRecord.payload();
    }

    @Transactional
    public void completeExport(WorkspaceExportRecord exportRecord) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            WorkspaceRecord workspace = workspaceStore.findById(exportRecord.workspaceId()).orElseThrow();
            payload.put("workspace", Map.of(
                    "id", workspace.id(),
                    "slug", workspace.slug(),
                    "displayName", workspace.displayName()));
            payload.put("members", workspaceStore.findActiveMembers(exportRecord.workspaceId()).stream()
                    .map(member -> Map.of(
                            "ownerId", member.ownerId(),
                            "ownerKey", member.ownerKey(),
                            "displayName", member.displayName(),
                            "role", member.role().name()))
                    .toList());
            payload.put("linksSummary", Map.of("activeLinks", workspaceEntitlementService.currentUsage(exportRecord.workspaceId()).activeLinksCurrent()));
            payload.put("links", exportLinks(exportRecord.workspaceId()));
            payload.put("clicksIncluded", exportRecord.includeClicks());
            payload.put("securityEventsIncluded", exportRecord.includeSecurityEvents());
            payload.put("abuseCasesIncluded", exportRecord.includeAbuseCases());
            payload.put("webhooksIncluded", exportRecord.includeWebhooks());
            payload.put("webhooks", exportRecord.includeWebhooks() ? exportWebhooks(exportRecord.workspaceId()) : List.of());
            var json = objectMapper.valueToTree(payload);
            long payloadSize = objectMapper.writeValueAsString(json).getBytes(StandardCharsets.UTF_8).length;
            if (payloadSize > runtimeProperties.getExports().getMaxPayloadBytes()) {
                throw new IllegalStateException("Workspace export payload exceeded configured limit");
            }
            workspaceExportStore.markReady(exportRecord.id(), json, payloadSize, OffsetDateTime.now(clock));
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_EXPORT_COMPLETED,
                    exportRecord.requestedByOwnerId(),
                    exportRecord.workspaceId(),
                    null,
                    "POST",
                    "/api/v1/workspaces/current/exports/" + exportRecord.id(),
                    null,
                    "Workspace export completed",
                    OffsetDateTime.now(clock));
        } catch (Exception exception) {
            workspaceExportStore.markFailed(exportRecord.id(), exception.getMessage(), OffsetDateTime.now(clock));
            securityEventStore.record(
                    SecurityEventType.WORKSPACE_EXPORT_FAILED,
                    exportRecord.requestedByOwnerId(),
                    exportRecord.workspaceId(),
                    null,
                    "POST",
                    "/api/v1/workspaces/current/exports/" + exportRecord.id(),
                    null,
                    "Workspace export failed",
                    OffsetDateTime.now(clock));
        }
    }

    private List<Map<String, Object>> exportLinks(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT slug, original_url, expires_at, title, tags_json, lifecycle_state
                FROM links
                WHERE workspace_id = ?
                  AND lifecycle_state = 'ACTIVE'
                ORDER BY slug ASC
                """,
                (resultSet, rowNum) -> toExportedLink(resultSet),
                workspaceId);
    }

    private Map<String, Object> toExportedLink(ResultSet resultSet) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("slug", resultSet.getString("slug"));
        item.put("originalUrl", resultSet.getString("original_url"));
        item.put("expiresAt", resultSet.getObject("expires_at", OffsetDateTime.class));
        item.put("title", resultSet.getString("title"));
        item.put("tags", deserializeStringList(resultSet.getString("tags_json")));
        item.put("lifecycleState", resultSet.getString("lifecycle_state"));
        return item;
    }

    private List<Map<String, Object>> exportWebhooks(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT name, callback_url, event_types_json
                FROM webhook_subscriptions
                WHERE workspace_id = ?
                  AND disabled_at IS NULL
                ORDER BY created_at DESC, id DESC
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", resultSet.getString("name"));
                    item.put("callbackUrl", resultSet.getString("callback_url"));
                    item.put("eventTypes", deserializeStringSet(resultSet.getString("event_types_json")));
                    return item;
                },
                workspaceId);
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workspace export JSON array could not be deserialized", exception);
        }
    }

    private Set<String> deserializeStringSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(deserializeStringList(json)));
    }
}
