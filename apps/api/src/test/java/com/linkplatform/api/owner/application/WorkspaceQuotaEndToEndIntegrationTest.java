package com.linkplatform.api.owner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linkplatform.api.link.application.LinkStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceQuotaEndToEndIntegrationTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private WorkspaceEntitlementService workspaceEntitlementService;

    @Autowired
    private OwnerApiKeyStore ownerApiKeyStore;

    @Autowired
    private WebhookSubscriptionsStore webhookSubscriptionsStore;

    @Autowired
    private WebhookEventPublisher webhookEventPublisher;

    @Autowired
    private WebhookDeliveryRelay webhookDeliveryRelay;

    @Autowired
    private LinkStore linkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quotasBlockGrowthAfterDowngradeWithoutDeletingExistingState() {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace("quota-e2e", "quota-e2e", false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        ensureOwner(3L, "quota-member");
        workspaceStore.addMember(workspace.id(), 3L, WorkspaceRole.ADMIN, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.PRO, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);

        ownerApiKeyStore.create(1L, workspace.id(), "quota-key", sha256("quota-key"), "quota-key", Set.of(ApiKeyScope.API_KEYS_READ), now, null, "test");
        insertActiveLink(workspace.id(), "quota-link-1");
        insertActiveLink(workspace.id(), "quota-link-2");
        webhookSubscriptionsStore.create(
                workspace.id(),
                "quota-webhook",
                "http://127.0.0.1:9999/hook",
                "quota-secret-hash",
                "quota-secret",
                true,
                Set.of(WebhookEventType.LINK_CREATED),
                now);

        jdbcTemplate.update(
                """
                UPDATE workspace_plans
                SET active_links_limit = 1,
                    members_limit = 1,
                    api_keys_limit = 1,
                    webhooks_limit = 1
                WHERE workspace_id = ?
                """,
                workspace.id());

        assertEquals(2L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND removed_at IS NULL", Long.class, workspace.id()));
        assertEquals(2L, linkStore.countActiveLinksByOwner(workspace.id()));
        assertEquals(1L, ownerApiKeyStore.findActiveByWorkspaceId(workspace.id(), now).size());
        assertEquals(1L, webhookSubscriptionsStore.countEnabledByWorkspaceId(workspace.id()));

        assertThrows(WorkspaceQuotaExceededException.class, () -> workspaceEntitlementService.enforceMembersQuota(workspace.id()));
        assertThrows(WorkspaceQuotaExceededException.class, () -> workspaceEntitlementService.enforceActiveLinksQuota(workspace.id(), linkStore.countActiveLinksByOwner(workspace.id())));
        assertThrows(WorkspaceQuotaExceededException.class, () -> workspaceEntitlementService.enforceApiKeysQuota(workspace.id(), now));
        assertThrows(WorkspaceQuotaExceededException.class, () -> workspaceEntitlementService.enforceWebhooksQuota(workspace.id(), webhookSubscriptionsStore.countEnabledByWorkspaceId(workspace.id())));
    }

    @Test
    void webhookUsageLedgerIncrementsOnActualDeliveryAttemptOnly() {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace("quota-webhook-usage", "quota-webhook-usage", false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.FREE, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);
        webhookSubscriptionsStore.create(
                workspace.id(),
                "quota-webhook",
                "http://127.0.0.1:1/hook",
                "usage-secret-hash",
                "usage-secret",
                true,
                Set.of(WebhookEventType.LINK_CREATED),
                now);

        webhookEventPublisher.publish(
                workspace.id(),
                WebhookEventType.LINK_CREATED,
                "usage-1",
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("slug", "usage-1"));
        assertEquals(0L, workspaceEntitlementService.currentUsage(workspace.id()).currentMonthWebhookDeliveries());

        webhookDeliveryRelay.relayDueDeliveries();

        assertEquals(1L, workspaceEntitlementService.currentUsage(workspace.id()).currentMonthWebhookDeliveries());
    }

    @Test
    void quotaExceededProblemDetailsStayStructurallyConsistentAcrossHttpFlows() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace("quota-http", "quota-http", false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.FREE, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);
        jdbcTemplate.update(
                """
                UPDATE workspace_plans
                SET active_links_limit = 0,
                    members_limit = 1,
                    api_keys_limit = 0,
                    webhooks_limit = 0
                WHERE workspace_id = ?
                """,
                workspace.id());
        String quotaKey = bootstrapWorkspaceApiKey(workspace.id(), "quota-http-key",
                "[\"members:write\",\"api_keys:write\",\"links:write\",\"webhooks:write\"]");
        ensureOwner(4L, "quota-member-http");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/members", workspace.slug())
                        .header("X-API-Key", quotaKey)
                        .header("X-Workspace-Slug", workspace.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":4,"role":"ADMIN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.quotaMetric").value("MEMBERS"))
                .andExpect(jsonPath("$.currentUsage").isNumber())
                .andExpect(jsonPath("$.limit").isNumber());

        mockMvc.perform(post("/api/v1/owner/api-keys")
                        .header("X-API-Key", quotaKey)
                        .header("X-Workspace-Slug", workspace.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"over-quota","scopes":["api_keys:read"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.quotaMetric").value("API_KEYS"))
                .andExpect(jsonPath("$.currentUsage").isNumber())
                .andExpect(jsonPath("$.limit").isNumber());

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", quotaKey)
                        .header("X-Workspace-Slug", workspace.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"quota-http-link","originalUrl":"https://example.com/quota-http-link"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.quotaMetric").value("ACTIVE_LINKS"))
                .andExpect(jsonPath("$.currentUsage").isNumber())
                .andExpect(jsonPath("$.limit").isNumber());

        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", quotaKey)
                        .header("X-Workspace-Slug", workspace.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"quota-http-webhook","callbackUrl":"http://127.0.0.1:9999/hook","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.quotaMetric").value("WEBHOOKS"))
                .andExpect(jsonPath("$.currentUsage").isNumber())
                .andExpect(jsonPath("$.limit").isNumber());
    }

    private void insertActiveLink(long workspaceId, String slug) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, workspace_id, lifecycle_state, abuse_status)
                VALUES (?, ?, ?, ?, 1, 1, ?, 'ACTIVE', 'ACTIVE')
                """,
                slug,
                "https://example.com/" + slug,
                OffsetDateTime.now(),
                "example.com",
                workspaceId);
    }

    private void ensureOwner(long ownerId, String ownerKey) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owners WHERE id = ?", Integer.class, ownerId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (?, ?, ?, 'FREE', ?)",
                ownerId,
                ownerKey,
                ownerKey,
                OffsetDateTime.now());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String bootstrapWorkspaceApiKey(long workspaceId, String plaintextKey, String scopesJson) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (1, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                """,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                scopesJson,
                OffsetDateTime.now());
        return plaintextKey;
    }
}
