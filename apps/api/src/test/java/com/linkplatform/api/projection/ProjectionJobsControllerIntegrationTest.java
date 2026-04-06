package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(properties = "link-platform.projection-jobs.chunk-size=10")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProjectionJobsControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectionJobRunner projectionJobRunner;

    @Test
    void projectionJobsPersistScopeAndScopedAndUnscopedRunsWork() throws Exception {
        insertLifecycleHistory(1L, "scope-1", "alpha", OffsetDateTime.parse("2026-04-06T10:00:00Z"));
        insertLifecycleHistory(2L, "scope-2", "beta", OffsetDateTime.parse("2026-04-06T10:05:00Z"));

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"LINK_CATALOG_REBUILD","ownerId":1,"slug":"alpha"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.slug").value("alpha"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.lastChunkAt").doesNotExist())
                .andExpect(jsonPath("$.processedItems").value(0))
                .andExpect(jsonPath("$.failedItems").value(0));

        projectionJobRunner.runPendingJobs();

        Integer scopedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_catalog_projection WHERE owner_id = 1 AND slug = 'alpha'",
                Integer.class);
        assertEquals(1, scopedCount);

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"LINK_CATALOG_REBUILD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.slug").doesNotExist());

        projectionJobRunner.runPendingJobs();

        mockMvc.perform(get("/api/v1/projection-jobs").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobType").value("LINK_CATALOG_REBUILD"))
                .andExpect(jsonPath("$[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastChunkAt").isNotEmpty())
                .andExpect(jsonPath("$[0].processedItems").isNumber())
                .andExpect(jsonPath("$[0].failedItems").value(0))
                .andExpect(jsonPath("$[0].lastError").doesNotExist());
    }

    @Test
    void failedScopedJobExposesFailedItemsAndLastError() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, published_at)
                VALUES ('bad-scope', 'CREATED', 'broken', '{bad-json', ?, ?)
                """,
                OffsetDateTime.parse("2026-04-06T10:00:00Z"),
                OffsetDateTime.parse("2026-04-06T10:00:00Z"));

        String responseBody = mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"LINK_CATALOG_REBUILD","slug":"broken"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long jobId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(responseBody)
                .get("id")
                .asLong();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> projectionJobRunner.runPendingJobs());

        mockMvc.perform(get("/api/v1/projection-jobs/{id}", jobId).header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.failedItems").value(1))
                .andExpect(jsonPath("$.lastError").isNotEmpty());
    }

    private void insertLifecycleHistory(long ownerId, String eventId, String slug, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, published_at)
                VALUES (?, 'CREATED', ?, ?, ?, ?)
                """,
                eventId,
                slug,
                """
                {"eventId":"%s","eventType":"CREATED","ownerId":%d,"slug":"%s","originalUrl":"https://example.com/%s","title":"%s","tags":["docs"],"hostname":"example.com","expiresAt":null,"lifecycleState":"ACTIVE","version":1,"occurredAt":"%s"}
                """.formatted(eventId, ownerId, slug, slug, slug, occurredAt),
                occurredAt,
                occurredAt);
    }
}
