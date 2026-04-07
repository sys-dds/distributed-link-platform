package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceExportService {

    private final WorkspaceExportStore workspaceExportStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final WorkspaceStore workspaceStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final SecurityEventStore securityEventStore;
    private final ObjectMapper objectMapper;
    private final com.linkplatform.api.runtime.LinkPlatformRuntimeProperties runtimeProperties;
    private final Clock clock;

    public WorkspaceExportService(
            WorkspaceExportStore workspaceExportStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            WorkspaceStore workspaceStore,
            OperatorActionLogStore operatorActionLogStore,
            SecurityEventStore securityEventStore,
            ObjectMapper objectMapper,
            com.linkplatform.api.runtime.LinkPlatformRuntimeProperties runtimeProperties) {
        this.workspaceExportStore = workspaceExportStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspaceStore = workspaceStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.securityEventStore = securityEventStore;
        this.objectMapper = objectMapper;
        this.runtimeProperties = runtimeProperties;
        this.clock = Clock.systemUTC();
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
            payload.put("clicksIncluded", exportRecord.includeClicks());
            payload.put("securityEventsIncluded", exportRecord.includeSecurityEvents());
            payload.put("abuseCasesIncluded", exportRecord.includeAbuseCases());
            payload.put("webhooksIncluded", exportRecord.includeWebhooks());
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
}
