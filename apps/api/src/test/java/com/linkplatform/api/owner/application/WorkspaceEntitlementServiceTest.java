package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceEntitlementServiceTest {

    @Autowired
    private WorkspaceEntitlementService workspaceEntitlementService;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private LinkPlatformRuntimeProperties runtimeProperties;

    @Test
    void testProfileExplicitlyEnablesInternalWebhookAllowances() {
        assertThat(runtimeProperties.getWebhooks().isAllowPrivateCallbackHosts()).isTrue();
        assertThat(runtimeProperties.getWebhooks().isAllowHttpCallbacks()).isTrue();
    }

    @Test
    void webhookQuotaExceptionsCarryStableStructuredFields() {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace("entitlement-test", "entitlement-test", false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.FREE, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);

        WorkspaceQuotaExceededException exception = assertThrows(
                WorkspaceQuotaExceededException.class,
                () -> workspaceEntitlementService.enforceWebhooksQuota(workspace.id(), 5));

        assertEquals(WorkspaceUsageMetric.WEBHOOKS, exception.quotaMetric());
        assertEquals(5L, exception.currentUsage());
        assertEquals(5L, exception.limit());
        assertEquals("Workspace webhook quota exceeded", exception.detail());
    }
}
