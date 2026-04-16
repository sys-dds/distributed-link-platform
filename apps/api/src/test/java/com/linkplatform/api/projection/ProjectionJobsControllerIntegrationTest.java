package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Autowired
    private ProjectionJobRunner projectionJobRunner;

    @Autowired
    private ProjectionJobStore projectionJobStore;

    @Test
    void projectionJobsPersistScopeAndScopedAndUnscopedRunsWork() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String personalWorkspaceSlug = personalWorkspaceSlug();
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
                .andExpect(jsonPath("$.workspaceSlug").value(personalWorkspaceSlug))
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
                .andExpect(jsonPath("$.workspaceSlug").value(personalWorkspaceSlug))
                .andExpect(jsonPath("$.slug").doesNotExist());

        projectionJobRunner.runPendingJobs();

        Integer convergedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_catalog_projection WHERE owner_id = 1 AND slug = 'alpha'",
                Integer.class);
        assertEquals(1, convergedCount);

        mockMvc.perform(get("/api/v1/projection-jobs").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobType").value("LINK_CATALOG_REBUILD"))
                .andExpect(jsonPath("$[0].workspaceSlug").value(personalWorkspaceSlug))
                .andExpect(jsonPath("$[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastChunkAt").isNotEmpty())
                .andExpect(jsonPath("$[0].processedItems").isNumber())
                .andExpect(jsonPath("$[0].failedItems").value(0))
                .andExpect(jsonPath("$[0].lastError").doesNotExist());
    }

    @Test
    void failedScopedJobExposesConsistentUnknownFailedItemsAndCompatibilityAlias() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String personalWorkspaceSlug = personalWorkspaceSlug();
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = 1",
                Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, published_at)
                VALUES ('bad-scope', 'CREATED', 'broken', ?, ?, ?)
                """,
                "{\"workspaceId\":" + workspaceId + ",\"ownerId\":1,\"slug\":\"broken\",bad-json",
                OffsetDateTime.parse("2026-04-06T10:00:00Z"),
                OffsetDateTime.parse("2026-04-06T10:00:00Z"));

        String responseBody = mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"LINK_CATALOG_REBUILD","slug":"broken"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value(personalWorkspaceSlug))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long jobId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(responseBody)
                .get("id")
                .asLong();

        assertThrows(IllegalArgumentException.class, () -> projectionJobRunner.runPendingJobs());

        JsonNode failedJob = jsonMapper.readTree(mockMvc.perform(get("/api/v1/projection-jobs/{id}", jobId)
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.failedItems").value(0))
                .andExpect(jsonPath("$.lastError").isNotEmpty())
                .andExpect(jsonPath("$.errorSummary").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(failedJob.path("errorSummary").asText()).isEqualTo(failedJob.path("lastError").asText());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM projection_jobs WHERE id = ?",
                        String.class,
                        jobId))
                .isEqualTo("FAILED");
    }

    @Test
    void controllerResponseMatchesPersistedProjectionProgressState() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String personalWorkspaceSlug = personalWorkspaceSlug();
        insertLifecycleHistory(1L, "progress-1", "gamma", OffsetDateTime.parse("2026-04-06T11:00:00Z"));
        insertLifecycleHistory(1L, "progress-2", "gamma", OffsetDateTime.parse("2026-04-06T11:01:00Z"));
        insertLifecycleHistory(1L, "progress-3", "gamma", OffsetDateTime.parse("2026-04-06T11:02:00Z"));

        String responseBody = mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"LINK_CATALOG_REBUILD","ownerId":1,"slug":"gamma"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.lastChunkAt").doesNotExist())
                .andExpect(jsonPath("$.workspaceSlug").value(personalWorkspaceSlug))
                .andExpect(jsonPath("$.processedItems").value(0))
                .andExpect(jsonPath("$.failedItems").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long jobId = jsonMapper.readTree(responseBody).path("id").asLong();

        projectionJobRunner.runPendingJobs();

        ProjectionJob persisted = jdbcTemplate.queryForObject(
                """
                SELECT id, job_type, status, requested_at, started_at, completed_at,
                       last_chunk_at, processed_count, processed_items, failed_items, checkpoint_id,
                       error_summary, last_error, claimed_by, claimed_until, owner_id, workspace_id, slug,
                       range_start, range_end, requested_by_owner_id, operator_note
                FROM projection_jobs
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new ProjectionJob(
                        resultSet.getLong("id"),
                        ProjectionJobType.valueOf(resultSet.getString("job_type")),
                        ProjectionJobStatus.valueOf(resultSet.getString("status")),
                        resultSet.getObject("requested_at", OffsetDateTime.class),
                        resultSet.getObject("started_at", OffsetDateTime.class),
                        resultSet.getObject("last_chunk_at", OffsetDateTime.class),
                        resultSet.getObject("completed_at", OffsetDateTime.class),
                        resultSet.getLong("processed_count"),
                        resultSet.getLong("processed_items"),
                        resultSet.getLong("failed_items"),
                        resultSet.getObject("checkpoint_id", Long.class),
                        resultSet.getString("error_summary"),
                        resultSet.getString("last_error"),
                        resultSet.getString("claimed_by"),
                        resultSet.getObject("claimed_until", OffsetDateTime.class),
                        resultSet.getObject("owner_id", Long.class),
                        resultSet.getObject("workspace_id", Long.class),
                        resultSet.getString("slug"),
                        resultSet.getObject("range_start", OffsetDateTime.class),
                        resultSet.getObject("range_end", OffsetDateTime.class),
                        resultSet.getObject("requested_by_owner_id", Long.class),
                        resultSet.getString("operator_note")),
                jobId);

        assertThat(persisted.status()).isEqualTo(ProjectionJobStatus.COMPLETED);
        JsonNode controllerJob = jsonMapper.readTree(mockMvc.perform(get("/api/v1/projection-jobs/{id}", jobId)
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(OffsetDateTime.parse(controllerJob.path("startedAt").asText())).isEqualTo(persisted.startedAt());
        assertThat(OffsetDateTime.parse(controllerJob.path("lastChunkAt").asText())).isEqualTo(persisted.lastChunkAt());
        assertThat(controllerJob.path("workspaceSlug").asText()).isEqualTo(personalWorkspaceSlug);
        assertThat(controllerJob.path("processedItems").asLong()).isEqualTo(persisted.processedItems());
        assertThat(controllerJob.path("processedCount").asLong()).isEqualTo(persisted.processedItems());
        assertThat(controllerJob.path("processedCount").asLong()).isEqualTo(controllerJob.path("processedItems").asLong());
        assertThat(controllerJob.path("failedItems").asLong()).isEqualTo(persisted.failedItems());
        assertThat(controllerJob.path("lastError").isMissingNode() || controllerJob.path("lastError").isNull()).isTrue();
        assertThat(controllerJob.path("errorSummary").isMissingNode() || controllerJob.path("errorSummary").isNull()).isTrue();
    }

    @Test
    void legacyGlobalProjectionStoreMethodsAreRemoved() {
        assertThrows(NoSuchMethodException.class, () -> ProjectionJobStore.class.getMethod("findRecent", int.class));
        assertThrows(NoSuchMethodException.class, () -> ProjectionJobStore.class.getMethod("findById", long.class));
        assertThrows(NoSuchMethodException.class, () -> ProjectionJobStore.class.getMethod("countQueued"));
        assertThrows(NoSuchMethodException.class, () -> ProjectionJobStore.class.getMethod("countActive"));
        assertThrows(
                NoSuchMethodException.class,
                () -> ProjectionJobStore.class.getMethod(
                        "claimNextQueued",
                        String.class,
                        OffsetDateTime.class,
                        OffsetDateTime.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> ProjectionJobStore.class.getMethod(
                        "markFailed",
                        long.class,
                        OffsetDateTime.class,
                        String.class));
    }

    private void insertLifecycleHistory(long ownerId, String eventId, String slug, OffsetDateTime occurredAt) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = ?",
                Long.class,
                ownerId);
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, published_at)
                VALUES (?, 'CREATED', ?, ?, ?, ?)
                """,
                eventId,
                slug,
                """
                {"eventId":"%s","eventType":"CREATED","ownerId":%d,"workspaceId":%d,"slug":"%s","originalUrl":"https://example.com/%s","title":"%s","tags":["docs"],"hostname":"example.com","expiresAt":null,"lifecycleState":"ACTIVE","version":1,"occurredAt":"%s"}
                """.formatted(eventId, ownerId, workspaceId, slug, slug, slug, occurredAt),
                occurredAt,
                occurredAt);
    }

    private String personalWorkspaceSlug() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return jdbcTemplate.queryForObject(
                "SELECT slug FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = 1",
                String.class);
    }
}
