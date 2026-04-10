package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkLifecycleState;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceImportService {

    private final WorkspaceImportStore workspaceImportStore;
    private final WorkspaceExportService workspaceExportService;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final WorkspacePermissionService workspacePermissionService;
    private final LinkStore linkStore;
    private final WebhookSubscriptionsStore webhookSubscriptionsStore;
    private final SecurityEventStore securityEventStore;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public WorkspaceImportService(
            WorkspaceImportStore workspaceImportStore,
            WorkspaceExportService workspaceExportService,
            WorkspaceEntitlementService workspaceEntitlementService,
            WorkspacePermissionService workspacePermissionService,
            LinkStore linkStore,
            WebhookSubscriptionsStore webhookSubscriptionsStore,
            SecurityEventStore securityEventStore,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.workspaceImportStore = workspaceImportStore;
        this.workspaceExportService = workspaceExportService;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspacePermissionService = workspacePermissionService;
        this.linkStore = linkStore;
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.securityEventStore = securityEventStore;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceImportRecord> list(WorkspaceAccessContext context) {
        workspacePermissionService.requireImportsRead(context);
        return workspaceImportStore.findByWorkspaceId(context.workspaceId(), 50);
    }

    @Transactional(readOnly = true)
    public WorkspaceImportRecord get(WorkspaceAccessContext context, long importId) {
        workspacePermissionService.requireImportsRead(context);
        return workspaceImportStore.findById(context.workspaceId(), importId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace import not found: " + importId));
    }

    @Transactional
    public WorkspaceImportRecord requestImport(
            WorkspaceAccessContext context,
            Long sourceExportId,
            JsonNode payloadJson,
            Boolean dryRun,
            Boolean overwriteConflicts) {
        workspacePermissionService.requireImportsWrite(context);
        requireImportsEnabled(context.workspaceId());
        JsonNode resolvedPayload = resolvePayload(context, sourceExportId, payloadJson);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WorkspaceImportRecord record = workspaceImportStore.create(
                context.workspaceId(),
                context.ownerId(),
                sourceExportId,
                dryRun == null || dryRun,
                overwriteConflicts != null && overwriteConflicts,
                resolvedPayload,
                now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_IMPORT_REQUESTED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/imports",
                null,
                "Workspace import requested",
                now);
        return record;
    }

    @Transactional
    public WorkspaceImportRecord applyImport(WorkspaceAccessContext context, long importId) {
        workspacePermissionService.requireImportsWrite(context);
        WorkspaceImportRecord record = workspaceImportStore.findById(context.workspaceId(), importId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace import not found: " + importId));
        requireReadyToApply(record);
        ImportExecution execution = execute(record, true);
        OffsetDateTime now = OffsetDateTime.now(clock);
        workspaceImportStore.markCompleted(record.id(), execution.summaryJson(), now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_IMPORT_COMPLETED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/imports/" + importId + "/apply",
                null,
                "Workspace import completed",
                now);
        return workspaceImportStore.findById(context.workspaceId(), importId).orElseThrow();
    }

    @Transactional
    public void processQueuedImport(WorkspaceImportRecord record) {
        try {
            ImportExecution execution = execute(record, !record.dryRun());
            completeQueuedImport(record, execution);
        } catch (Exception exception) {
            failQueuedImport(record, exception);
        }
    }

    private ImportExecution execute(WorkspaceImportRecord record, boolean apply) {
        validatePayload(record.payloadJson());
        SummaryAccumulator summary = new SummaryAccumulator();
        processLinks(record, apply, summary);
        processWebhooks(record, apply, summary);
        return new ImportExecution(summary.toJson(objectMapper));
    }

    private void processLinks(WorkspaceImportRecord record, boolean apply, SummaryAccumulator summary) {
        for (JsonNode item : arrayOrEmpty(record.payloadJson().path("links"))) {
            try {
                processLink(record, apply, summary, ImportedLink.from(item));
            } catch (Exception exception) {
                summary.addSkipped("link");
            }
        }
    }

    private void processWebhooks(WorkspaceImportRecord record, boolean apply, SummaryAccumulator summary) {
        if (!record.payloadJson().path("webhooksIncluded").asBoolean(false)) {
            return;
        }
        for (JsonNode item : arrayOrEmpty(record.payloadJson().path("webhooks"))) {
            try {
                processWebhook(record, apply, ImportedWebhook.from(item));
            } catch (Exception exception) {
                summary.addSkipped("webhook");
            }
        }
    }

    private void requireImportsEnabled(long workspaceId) {
        if (!workspaceEntitlementService.exportsEnabled(workspaceId)) {
            throw new IllegalArgumentException("Workspace imports are not enabled for this plan");
        }
    }

    private JsonNode resolvePayload(WorkspaceAccessContext context, Long sourceExportId, JsonNode payloadJson) {
        if ((sourceExportId == null) == (payloadJson == null)) {
            throw new IllegalArgumentException("Exactly one of sourceExportId or payloadJson must be provided");
        }
        if (sourceExportId != null) {
            return workspaceExportService.resolveImportPayload(context, sourceExportId);
        }
        return payloadJson;
    }

    private void requireReadyToApply(WorkspaceImportRecord record) {
        if (!"READY_TO_APPLY".equals(record.status())) {
            throw new IllegalArgumentException("Workspace import is not ready to apply");
        }
    }

    private void completeQueuedImport(WorkspaceImportRecord record, ImportExecution execution) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (record.dryRun()) {
            workspaceImportStore.markReadyToApply(record.id(), execution.summaryJson(), now);
        } else {
            workspaceImportStore.markCompleted(record.id(), execution.summaryJson(), now);
        }
        securityEventStore.record(
                SecurityEventType.WORKSPACE_IMPORT_COMPLETED,
                record.requestedByOwnerId(),
                record.workspaceId(),
                null,
                "POST",
                "/api/v1/workspaces/current/imports/" + record.id(),
                null,
                "Workspace import completed",
                now);
    }

    private void failQueuedImport(WorkspaceImportRecord record, Exception exception) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        workspaceImportStore.markFailed(record.id(), exception.getMessage(), now);
        securityEventStore.record(
                SecurityEventType.WORKSPACE_IMPORT_FAILED,
                record.requestedByOwnerId(),
                record.workspaceId(),
                null,
                "POST",
                "/api/v1/workspaces/current/imports/" + record.id(),
                null,
                "Workspace import failed",
                now);
    }

    private void processLink(
            WorkspaceImportRecord record,
            boolean apply,
            SummaryAccumulator summary,
            ImportedLink importedLink) {
        LinkDetails existing = findExistingLink(record.workspaceId(), importedLink.slug());
        if (hasLinkConflict(record, importedLink, existing, summary)) {
            return;
        }
        if (!apply) {
            summarizeLinkPreview(summary, existing);
            return;
        }
        boolean changed = existing == null
                ? createImportedLink(record, summary, importedLink)
                : updateImportedLink(record, summary, importedLink, existing);
        if (!changed) {
            return;
        }
        applyImportedLifecycle(record.workspaceId(), importedLink);
    }

    private LinkDetails findExistingLink(long workspaceId, String slug) {
        return linkStore.findStoredDetailsBySlug(slug, workspaceId).orElse(null);
    }

    private boolean hasLinkConflict(
            WorkspaceImportRecord record,
            ImportedLink importedLink,
            LinkDetails existing,
            SummaryAccumulator summary) {
        if (existing == null || record.overwriteConflicts()) {
            return false;
        }
        summary.addConflict("link:" + importedLink.slug());
        return true;
    }

    private void summarizeLinkPreview(SummaryAccumulator summary, LinkDetails existing) {
        if (existing == null) {
            summary.linksCreated++;
            return;
        }
        summary.linksUpdated++;
    }

    private boolean createImportedLink(
            WorkspaceImportRecord record,
            SummaryAccumulator summary,
            ImportedLink importedLink) {
        workspaceEntitlementService.enforceActiveLinksQuota(
                record.workspaceId(),
                linkStore.countActiveLinksByOwner(record.workspaceId()));
        boolean saved = linkStore.save(
                new Link(new LinkSlug(importedLink.slug()), new OriginalUrl(importedLink.originalUrl())),
                importedLink.expiresAt(),
                importedLink.title(),
                importedLink.tags(),
                hostname(importedLink.originalUrl()),
                1L,
                record.workspaceId());
        if (!saved) {
            summary.addConflict("link:" + importedLink.slug());
            return false;
        }
        summary.linksCreated++;
        return true;
    }

    private boolean updateImportedLink(
            WorkspaceImportRecord record,
            SummaryAccumulator summary,
            ImportedLink importedLink,
            LinkDetails existing) {
        boolean updated = linkStore.update(
                importedLink.slug(),
                importedLink.originalUrl(),
                importedLink.expiresAt(),
                importedLink.title(),
                importedLink.tags(),
                hostname(importedLink.originalUrl()),
                existing.version(),
                existing.version() + 1,
                record.workspaceId());
        if (!updated) {
            summary.addConflict("link:" + importedLink.slug());
            return false;
        }
        summary.linksUpdated++;
        return true;
    }

    private void processWebhook(WorkspaceImportRecord record, boolean apply, ImportedWebhook importedWebhook) {
        if (!apply) {
            return;
        }
        webhookSubscriptionsStore.create(
                record.workspaceId(),
                importedWebhook.name(),
                importedWebhook.callbackUrl(),
                "imported-disabled",
                "imported",
                false,
                importedWebhook.eventTypes(),
                OffsetDateTime.now(clock));
    }

    private void applyImportedLifecycle(long workspaceId, ImportedLink importedLink) {
        LinkLifecycleState state = importedLink.lifecycleState();
        if (state == LinkLifecycleState.ACTIVE
                || state == LinkLifecycleState.EXPIRED
                || state == LinkLifecycleState.ALL) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE links
                SET lifecycle_state = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND slug = ?
                  AND deleted_at IS NULL
                """,
                state.name(),
                OffsetDateTime.now(clock),
                workspaceId,
                importedLink.slug());
    }

    private void validatePayload(JsonNode payloadJson) {
        if (payloadJson == null || !payloadJson.isObject()) {
            throw new IllegalArgumentException("Workspace import payload must be a JSON object");
        }
        if (!payloadJson.path("links").isArray() && !payloadJson.path("links").isMissingNode()) {
            throw new IllegalArgumentException("Workspace import payload links must be an array");
        }
        if (!payloadJson.path("webhooks").isArray() && !payloadJson.path("webhooks").isMissingNode()) {
            throw new IllegalArgumentException("Workspace import payload webhooks must be an array");
        }
    }

    private List<JsonNode> arrayOrEmpty(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false).toList();
    }

    private String hostname(String originalUrl) {
        return URI.create(originalUrl).getHost();
    }

    private record ImportExecution(JsonNode summaryJson) {
    }

    private static final class SummaryAccumulator {
        private long linksCreated;
        private long linksUpdated;
        private long conflicts;
        private long skipped;
        private final List<String> conflictDetails = new java.util.ArrayList<>();
        private final List<String> skippedDetails = new java.util.ArrayList<>();

        private void addConflict(String detail) {
            conflicts++;
            conflictDetails.add(detail);
        }

        private void addSkipped(String detail) {
            skipped++;
            skippedDetails.add(detail);
        }

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("linksCreated", linksCreated);
            node.put("linksUpdated", linksUpdated);
            node.put("conflicts", conflicts);
            node.put("skipped", skipped);
            ArrayNode conflictsNode = node.putArray("conflictDetails");
            conflictDetails.forEach(conflictsNode::add);
            ArrayNode skippedNode = node.putArray("skippedDetails");
            skippedDetails.forEach(skippedNode::add);
            return node;
        }
    }

    private record ImportedLink(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            LinkLifecycleState lifecycleState) {

        private static ImportedLink from(JsonNode node) {
            String lifecycle = node.path("lifecycleState").asText("ACTIVE").trim().toUpperCase(Locale.ROOT);
            List<String> tags = readTags(node.path("tags"));
            return new ImportedLink(
                    node.path("slug").asText(),
                    node.path("originalUrl").asText(),
                    readOptionalOffsetDateTime(node.path("expiresAt")),
                    node.path("title").isMissingNode() || node.path("title").isNull()
                            ? null
                            : node.path("title").asText(),
                    tags,
                    LinkLifecycleState.valueOf(lifecycle));
        }

        private static List<String> readTags(JsonNode tagsNode) {
            List<String> tags = new java.util.ArrayList<>();
            if (!tagsNode.isArray()) {
                return tags;
            }
            for (JsonNode tag : tagsNode) {
                tags.add(tag.asText());
            }
            return tags;
        }

        private static OffsetDateTime readOptionalOffsetDateTime(JsonNode node) {
            if (node.isNull() || node.isMissingNode()) {
                return null;
            }
            return OffsetDateTime.parse(node.asText());
        }
    }

    private record ImportedWebhook(String name, String callbackUrl, Set<WebhookEventType> eventTypes) {

        private static ImportedWebhook from(JsonNode node) {
            Set<WebhookEventType> eventTypes = new LinkedHashSet<>();
            for (JsonNode eventType : node.path("eventTypes")) {
                eventTypes.add(WebhookEventType.fromValue(eventType.asText()));
            }
            if (eventTypes.isEmpty()) {
                throw new IllegalArgumentException("Imported webhook eventTypes are required");
            }
            return new ImportedWebhook(
                    node.path("name").asText(),
                    node.path("callbackUrl").asText(),
                    Set.copyOf(eventTypes));
        }
    }
}
