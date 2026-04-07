package com.linkplatform.api.owner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceRetentionEndToEndIntegrationTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private WorkspaceRetentionService workspaceRetentionService;

    @Autowired
    private WorkspaceRetentionPurgeRunner workspaceRetentionPurgeRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void purgeDeletesOnlyEligibleRecordsForActiveWorkspace() {
        WorkspaceAccessContext workspaceA = createWorkspaceContext("retention-a");
        WorkspaceAccessContext workspaceB = createWorkspaceContext("retention-b");

        workspaceRetentionService.updatePolicy(workspaceA.workspaceId(), 7, 7, 7, 7, 7, workspaceA.ownerId());
        workspaceRetentionService.updatePolicy(workspaceB.workspaceId(), 7, 7, 7, 7, 7, workspaceB.ownerId());

        OffsetDateTime oldTimestamp = OffsetDateTime.now().minusDays(30);
        insertSecurityEvent(workspaceA.workspaceId(), oldTimestamp, "Workspace A event");
        insertSecurityEvent(workspaceB.workspaceId(), oldTimestamp, "Workspace B event");
        insertOperatorAction(workspaceA.workspaceId(), oldTimestamp, "Workspace A action");
        insertOperatorAction(workspaceB.workspaceId(), oldTimestamp, "Workspace B action");

        WorkspaceRetentionPurgeRunner.PurgeResult result = workspaceRetentionPurgeRunner.runForWorkspace(workspaceA);

        assertEquals(1L, result.securityEventsDeleted());
        assertEquals(1L, result.operatorActionsDeleted());
        assertEquals(0L, result.webhookDeliveriesDeleted());
        assertEquals(0L, result.abuseCasesDeleted());
        assertEquals(0L, result.clickHistoryDeleted());

        assertEquals(0L, countRows("owner_security_events", workspaceA.workspaceId()));
        assertEquals(1L, countRows("owner_security_events", workspaceB.workspaceId()));
        assertEquals(1L, countRows("operator_action_log", workspaceA.workspaceId()));
        assertEquals(1L, countRows("operator_action_log", workspaceB.workspaceId()));
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

    private void insertSecurityEvent(long workspaceId, OffsetDateTime occurredAt, String summary) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_security_events (
                    owner_id, workspace_id, api_key_hash, request_method, request_path,
                    remote_address, event_type, event_summary, occurred_at
                ) VALUES (1, ?, NULL, 'POST', '/test', '127.0.0.1', 'WORKSPACE_RETENTION_PURGE_RUN', ?, ?)
                """,
                workspaceId,
                summary,
                occurredAt);
    }

    private void insertOperatorAction(long workspaceId, OffsetDateTime createdAt, String note) {
        jdbcTemplate.update(
                """
                INSERT INTO operator_action_log (
                    workspace_id, owner_id, subsystem, action_type, target_slug,
                    target_case_id, target_projection_job_id, note, created_at
                ) VALUES (?, 1, 'PIPELINE', 'workspace_retention_update', NULL, NULL, NULL, ?, ?)
                """,
                workspaceId,
                note,
                createdAt);
    }

    private long countRows(String table, long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE workspace_id = ?",
                Long.class,
                workspaceId);
        return count == null ? 0L : count;
    }
}
