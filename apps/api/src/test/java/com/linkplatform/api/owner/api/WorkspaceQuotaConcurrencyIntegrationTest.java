package com.linkplatform.api.owner.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.WorkspacePlanCode;
import com.linkplatform.api.owner.application.WorkspacePlanStore;
import com.linkplatform.api.owner.application.WorkspaceRecord;
import com.linkplatform.api.owner.application.WorkspaceRole;
import com.linkplatform.api.owner.application.WorkspaceStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceQuotaConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Test
    void concurrentWorkspaceLinkCreatesCannotBothConsumeLastActiveLinkQuotaUnit() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace("quota-race", "quota-race", false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        ensureOwner(3L, "quota-race-member");
        workspaceStore.addMember(workspace.id(), 3L, WorkspaceRole.ADMIN, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.PRO, now);
        jdbcTemplate.update("UPDATE workspace_plans SET active_links_limit = 1 WHERE workspace_id = ?", workspace.id());

        String ownerKey = bootstrapWorkspaceApiKey(1L, workspace.id(), "quota-race-owner-key");
        String memberKey = bootstrapWorkspaceApiKey(3L, workspace.id(), "quota-race-member-key");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<MvcResult>> futures = List.of(
                executorService.submit(createLinkRequest(ready, start, ownerKey, workspace.slug(), "quota-race-a")),
                executorService.submit(createLinkRequest(ready, start, memberKey, workspace.slug(), "quota-race-b")));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(10, TimeUnit.SECONDS);
            statuses.add(result.getResponse().getStatus());
        }
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, statuses.stream().filter(status -> status == 201).count());
        assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        assertEquals(1L, count("SELECT COUNT(*) FROM links WHERE workspace_id = ?", workspace.id()));
        assertEquals(1L, currentActiveLinksSnapshot(workspace.id()));
    }

    private Callable<MvcResult> createLinkRequest(
            CountDownLatch ready,
            CountDownLatch start,
            String apiKey,
            String workspaceSlug,
            String slug) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return mockMvc.perform(post("/api/v1/links")
                            .header("X-API-Key", apiKey)
                            .header("X-Workspace-Slug", workspaceSlug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"slug":"%s","originalUrl":"https://example.com/%s"}
                                    """.formatted(slug, slug)))
                    .andReturn();
        };
    }

    private String bootstrapWorkspaceApiKey(long ownerId, long workspaceId, String plaintextKey) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'quota-concurrency-test')
                """,
                ownerId,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                scopesJson(),
                OffsetDateTime.now());
        return plaintextKey;
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

    private String scopesJson() {
        return "[\"" + ApiKeyScope.LINKS_WRITE.value() + "\"]";
    }

    private long count(String sql, Object... parameters) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return count == null ? 0L : count;
    }

    private long currentActiveLinksSnapshot(long workspaceId) {
        Long quantity = jdbcTemplate.query(
                        """
                        SELECT quantity
                        FROM workspace_usage_ledger
                        WHERE workspace_id = ?
                          AND metric_code = 'ACTIVE_LINKS'
                        ORDER BY recorded_at DESC, id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getLong("quantity"),
                        workspaceId)
                .stream()
                .findFirst()
                .orElse(0L);
        return quantity == null ? 0L : quantity;
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
}
