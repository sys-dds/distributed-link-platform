package com.linkplatform.api.owner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceExportEndToEndIntegrationTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private WorkspaceExportService workspaceExportService;

    @Autowired
    private WorkspaceExportRunner workspaceExportRunner;

    @Autowired
    private WorkspaceExportStore workspaceExportStore;

    @Test
    void requestAndRunnerProduceReadyExportWithoutSecrets() {
        WorkspaceAccessContext context = createWorkspaceContext("export-e2e");
        WorkspaceExportRecord requested = workspaceExportService.requestExport(context, false, true, true, true);
        assertEquals("QUEUED", requested.status());

        workspaceExportRunner.runQueuedExports();

        WorkspaceExportRecord completed = workspaceExportStore.findById(context.workspaceId(), requested.id()).orElseThrow();
        assertEquals("READY", completed.status());
        assertNotNull(completed.payload());
        assertNotNull(completed.completedAt());
        assertTrue(completed.payloadSizeBytes() != null && completed.payloadSizeBytes() > 0);
        assertEquals(context.workspaceSlug(), completed.payload().path("workspace").path("slug").asText());
        String payloadText = completed.payload().toString();
        assertFalse(payloadText.contains("signingSecret"));
        assertFalse(payloadText.contains("plaintext"));
        assertTrue(payloadText.contains("members"));
        assertTrue(payloadText.contains("linksSummary"));
    }

    private WorkspaceAccessContext createWorkspaceContext(String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace(slug, slug, false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.FREE, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);
        return new WorkspaceAccessContext(
                new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE),
                workspace.id(),
                workspace.slug(),
                workspace.displayName(),
                false,
                WorkspaceRole.OWNER,
                WorkspaceRole.OWNER.impliedScopes(),
                "test-api-key-hash");
    }
}
