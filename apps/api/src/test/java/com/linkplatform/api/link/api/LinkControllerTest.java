package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkControllerTest {

    private static final String FREE_API_KEY = "free-owner-api-key";
    private static final String PRO_API_KEY = "pro-owner-api-key";
    private static final String INVALID_API_KEY = "invalid-owner-api-key";
    private static final long FREE_OWNER_ID = 1L;
    private static final long PRO_OWNER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void validApiKeyCanCreateLinkAndStoreOwnerId() throws Exception {
        mockMvc.perform(mutationPost(FREE_API_KEY, "/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "launch-page",
                                  "originalUrl": "https://example.com/launch"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("launch-page"))
                .andExpect(jsonPath("$.version").value(1));

        assertEquals(
                FREE_OWNER_ID,
                jdbcTemplate.queryForObject("SELECT owner_id FROM links WHERE slug = 'launch-page'", Long.class));
    }

    @Test
    void missingApiKeyFailsCleanlyOnProtectedMutationEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "missing-auth",
                                  "originalUrl": "https://example.com/missing-auth"
                                }
                                """))
                .andExpect(problemDetail(401, "Unauthorized", "X-API-Key header is required"));
    }

    @Test
    void invalidApiKeyFailsCleanlyAndCreatesSecurityEvent() throws Exception {
        mockMvc.perform(mutationPost(INVALID_API_KEY, "/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "invalid-auth",
                                  "originalUrl": "https://example.com/invalid-auth"
                                }
                                """))
                .andExpect(problemDetail(401, "Unauthorized", "X-API-Key is invalid"));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'INVALID_API_KEY'",
                        Integer.class));
    }

    @Test
    void updateAndDeleteCannotMutateAnotherOwnersLink() throws Exception {
        createOwnedLink(FREE_API_KEY, "owned-link", "https://example.com/owned-link");

        mockMvc.perform(mutationPut(PRO_API_KEY, "/api/v1/links/owned-link")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/hijack"
                                }
                                """))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: owned-link"));

        mockMvc.perform(mutationDelete(PRO_API_KEY, "/api/v1/links/owned-link").header("If-Match", "1"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: owned-link"));
    }

    @Test
    void ownerScopedDetailListSearchAndSuggestionsDoNotLeakCrossOwnerData() throws Exception {
        insertOwnedLink(FREE_OWNER_ID, "alpha-free", "https://docs.example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null, "Alpha Guide", "[\"docs\"]");
        insertOwnedLink(PRO_OWNER_ID, "alpha-pro", "https://app.example.com/alpha", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null, "Alpha App", "[\"product\"]");

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/alpha-free"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("alpha-free"));

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/alpha-pro"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: alpha-pro"));

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links").param("state", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha-free"));

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links").param("q", "guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha-free"));

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/suggestions").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha-free"));
    }

    @Test
    void discoveryQuerySupportsOwnerScopedFiltersSortsAndCursorPaging() throws Exception {
        insertOwnedLink(FREE_OWNER_ID, "alpha-free", "https://docs.example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null, "Alpha Guide", "[\"docs\"]");
        insertOwnedLink(FREE_OWNER_ID, "beta-free", "https://docs.example.com/beta", OffsetDateTime.parse("2026-04-01T09:00:00Z"), OffsetDateTime.parse("2020-04-01T08:00:00Z"), "Beta Guide", "[\"docs\",\"beta\"]");
        insertOwnedLink(PRO_OWNER_ID, "alpha-pro", "https://app.example.com/alpha", OffsetDateTime.parse("2026-04-01T10:00:00Z"), null, "Alpha App", "[\"product\"]");
        jdbcTemplate.update("UPDATE link_discovery_projection SET updated_at = ? WHERE slug = ?", OffsetDateTime.parse("2026-04-02T08:00:00Z"), "alpha-free");
        jdbcTemplate.update("UPDATE link_discovery_projection SET updated_at = ? WHERE slug = ?", OffsetDateTime.parse("2026-04-03T08:00:00Z"), "beta-free");
        jdbcTemplate.update("UPDATE link_discovery_projection SET updated_at = ? WHERE slug = ?", OffsetDateTime.parse("2026-04-04T08:00:00Z"), "alpha-pro");

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/discovery")
                        .param("q", "guide")
                        .param("hostname", "docs.example.com")
                        .param("tag", "docs")
                        .param("lifecycle", "all")
                        .param("sort", "updated_desc")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("beta-free"))
                .andExpect(jsonPath("$.items[0].lifecycleState").value("expired"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());

        String firstPageJson = mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/discovery")
                        .param("q", "guide")
                        .param("hostname", "docs.example.com")
                        .param("tag", "docs")
                        .param("lifecycle", "all")
                        .param("sort", "updated_desc")
                        .param("limit", "1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String cursor = objectMapper.readTree(firstPageJson).get("nextCursor").asText();
        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/links/discovery")
                        .param("q", "guide")
                        .param("hostname", "docs.example.com")
                        .param("tag", "docs")
                        .param("lifecycle", "all")
                        .param("sort", "updated_desc")
                        .param("limit", "1")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("alpha-free"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void meReturnsOwnerPlanAndQuotaSummary() throws Exception {
        createOwnedLink(FREE_API_KEY, "me-one", "https://example.com/me-one");

        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerKey").value("free-owner"))
                .andExpect(jsonPath("$.displayName").value("Free Owner"))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.activeLinkCount").value(1))
                .andExpect(jsonPath("$.activeLinkLimit").value(2));
    }

    @Test
    void freePlanQuotaRejectsCreateAndRecordsSecurityEvent() throws Exception {
        createOwnedLink(FREE_API_KEY, "free-one", "https://example.com/free-one");
        createOwnedLink(FREE_API_KEY, "free-two", "https://example.com/free-two");

        mockMvc.perform(mutationPost(FREE_API_KEY, "/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "free-three",
                                  "originalUrl": "https://example.com/free-three"
                                }
                                """))
                .andExpect(problemDetail(
                        409,
                        "Conflict",
                        "Active link quota exceeded for owner free-owner on plan FREE"));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'QUOTA_REJECTED' AND owner_id = 1",
                        Integer.class));
    }

    @Test
    void proPlanCanExceedFreeThresholdUsedInTests() throws Exception {
        createOwnedLink(PRO_API_KEY, "pro-one", "https://example.com/pro-one");
        createOwnedLink(PRO_API_KEY, "pro-two", "https://example.com/pro-two");

        mockMvc.perform(mutationPost(PRO_API_KEY, "/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "pro-three",
                                  "originalUrl": "https://example.com/pro-three"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("pro-three"));
    }

    @Test
    void freeAndProReadRateLimitsDifferAndRejectionsAreRecorded() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/me")).andExpect(status().isOk());
        }
        mockMvc.perform(readGet(FREE_API_KEY, "/api/v1/me"))
                .andExpect(problemDetail(429, "Too Many Requests", "Control-plane read rate limit exceeded"));

        for (int attempt = 1; attempt <= 6; attempt++) {
            mockMvc.perform(readGet(PRO_API_KEY, "/api/v1/me")).andExpect(status().isOk());
        }

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'RATE_LIMIT_REJECTED' AND owner_id = 1",
                        Integer.class));
    }

    @Test
    void sameIdempotencyKeyUsedByDifferentOwnersDoesNotCollide() throws Exception {
        mockMvc.perform(mutationPost(FREE_API_KEY, "/api/v1/links")
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "free-idempotent",
                                  "originalUrl": "https://example.com/free-idempotent"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(mutationPost(PRO_API_KEY, "/api/v1/links")
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "pro-idempotent",
                                  "originalUrl": "https://example.com/pro-idempotent"
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_mutation_idempotency WHERE idempotency_key = 'shared-key'",
                        Integer.class));
    }

    @Test
    void plaintextApiKeyIsNotStoredInDatabase() {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_api_keys WHERE key_hash = '5cd81fa8d1b30a1619001c1fd727555a9a17cc23d551587bb214dccbd1f59606'",
                        Integer.class));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_api_keys WHERE key_hash = ?",
                        Integer.class,
                        FREE_API_KEY));
    }

    @Test
    void readsRequireApiKeyToo() throws Exception {
        insertOwnedLink(FREE_OWNER_ID, "read-auth", "https://example.com/read-auth", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null, null, null);

        mockMvc.perform(get("/api/v1/links/read-auth"))
                .andExpect(problemDetail(401, "Unauthorized", "X-API-Key header is required"));
    }

    @Test
    void updateWithCorrectIfMatchSucceedsAndIncrementsVersion() throws Exception {
        createOwnedLink(FREE_API_KEY, "editable", "https://example.com/original");

        mockMvc.perform(mutationPut(FREE_API_KEY, "/api/v1/links/editable")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/updated"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void lifecycleOutboxBehaviorForWritesStillPasses() throws Exception {
        createOwnedLink(FREE_API_KEY, "async-feed", "https://example.com/async-feed");

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_lifecycle_outbox WHERE event_type = 'CREATED' AND event_key = 'async-feed'",
                        Integer.class));

        mockMvc.perform(mutationPut(FREE_API_KEY, "/api/v1/links/async-feed")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/async-feed-v2"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(mutationDelete(FREE_API_KEY, "/api/v1/links/async-feed").header("If-Match", "2"))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM link_activity_events", Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_lifecycle_outbox WHERE event_type = 'UPDATED' AND event_key = 'async-feed'",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_lifecycle_outbox WHERE event_type = 'DELETED' AND event_key = 'async-feed'",
                        Integer.class));
    }

    private void createOwnedLink(String apiKey, String slug, String originalUrl) throws Exception {
        mockMvc.perform(mutationPost(apiKey, "/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "%s",
                                  "originalUrl": "%s"
                                }
                                """.formatted(slug, originalUrl)))
                .andExpect(status().isCreated());
    }

    private void insertOwnedLink(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson) {
        String hostname = URI.create(originalUrl).getHost().toLowerCase();
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, expires_at, title, tags_json, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                expiresAt,
                title,
                tagsJson,
                hostname,
                ownerId);
        jdbcTemplate.update(
                """
                INSERT INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, deleted_at, version, owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                createdAt,
                title,
                tagsJson,
                hostname,
                expiresAt,
                ownerId);
        insertDiscoveryProjection(ownerId, slug, originalUrl, createdAt, createdAt, expiresAt, title, tagsJson, hostname, null, expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now()) ? "EXPIRED" : "ACTIVE", 1L);
    }

    private void insertDiscoveryProjection(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson,
            String hostname,
            OffsetDateTime deletedAt,
            String lifecycleState,
            long version) {
        jdbcTemplate.update(
                """
                INSERT INTO link_discovery_projection (
                    slug, owner_id, original_url, title, hostname, tags_json, created_at, updated_at, expires_at, deleted_at, lifecycle_state, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                ownerId,
                originalUrl,
                title,
                hostname,
                tagsJson,
                createdAt,
                updatedAt,
                expiresAt,
                deletedAt,
                lifecycleState,
                version);
    }

    private MockHttpServletRequestBuilder mutationPost(String apiKey, String path) {
        return post(path).header("X-API-Key", apiKey);
    }

    private MockHttpServletRequestBuilder mutationPut(String apiKey, String path) {
        return put(path).header("X-API-Key", apiKey);
    }

    private MockHttpServletRequestBuilder mutationDelete(String apiKey, String path) {
        return delete(path).header("X-API-Key", apiKey);
    }

    private MockHttpServletRequestBuilder readGet(String apiKey, String path) {
        return get(path).header("X-API-Key", apiKey);
    }

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
        };
    }
}
