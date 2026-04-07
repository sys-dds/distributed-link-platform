package com.linkplatform.api.link.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LifecyclePipelineControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void pauseResumeForceTickAndStatusFieldsWorkAndEmitSecurityEvents() throws Exception {
        insertOutbox("lifecycle-parked", OffsetDateTime.parse("2026-04-06T10:00:00Z"), OffsetDateTime.parse("2026-04-06T10:10:00Z"));
        insertOutbox("lifecycle-eligible", OffsetDateTime.parse("2026-04-06T08:00:00Z"), null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/pause")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"maintenance window"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("lifecycle"))
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.pauseReason").value("maintenance window"))
                .andExpect(jsonPath("$.eligibleCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(1));

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/resume")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.pauseReason").doesNotExist());

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/force-tick")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("lifecycle"))
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.processedCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(0))
                .andExpect(jsonPath("$.eligibleCountAfter").value(0))
                .andExpect(jsonPath("$.parkedCountAfter").value(1));

        mockMvc.perform(get("/api/v1/lifecycle/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("lifecycle"))
                .andExpect(jsonPath("$.oldestParkedAgeSeconds").isNumber())
                .andExpect(jsonPath("$.lastForceTickAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRelaySuccessAt").isNotEmpty());

        Integer securityEvents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM owner_security_events
                WHERE owner_id = 1
                  AND event_type IN ('LIFECYCLE_PIPELINE_PAUSED', 'LIFECYCLE_PIPELINE_RESUMED', 'LIFECYCLE_PIPELINE_FORCE_TICKED')
                """,
                Integer.class);
        Integer operatorActions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operator_action_log WHERE subsystem = 'PIPELINE' AND action_type IN ('lifecycle_pipeline_pause', 'lifecycle_pipeline_resume', 'lifecycle_pipeline_force_tick')",
                Integer.class);
        Assertions.assertEquals(3, securityEvents);
        Assertions.assertEquals(3, operatorActions);
    }

    @Test
    void drainEndpointRespectsDefaultAndMaxCapAndStatusCountsStayHonest() throws Exception {
        for (int index = 0; index < 120; index++) {
            insertOutbox("lifecycle-default-" + index, OffsetDateTime.parse("2026-04-06T10:00:00Z"), OffsetDateTime.parse("2026-04-06T10:10:00Z"));
        }

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/parked/drain")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLimit").value(100))
                .andExpect(jsonPath("$.appliedLimit").value(100))
                .andExpect(jsonPath("$.movedCount").value(100))
                .andExpect(jsonPath("$.remainingParkedCount").value(20))
                .andExpect(jsonPath("$.lastRequeueAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/lifecycle/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkedCount").value(20))
                .andExpect(jsonPath("$.eligibleCount").value(100))
                .andExpect(jsonPath("$.lastRequeueAt").isNotEmpty());

        for (int index = 0; index < 120; index++) {
            insertOutbox("lifecycle-max-" + index, OffsetDateTime.parse("2026-04-06T10:00:00Z"), OffsetDateTime.parse("2026-04-06T10:10:00Z"));
        }

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/parked/drain")
                        .header("X-API-Key", FREE_API_KEY)
                        .param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLimit").value(999))
                .andExpect(jsonPath("$.appliedLimit").value(500))
                .andExpect(jsonPath("$.movedCount").value(140))
                .andExpect(jsonPath("$.remainingParkedCount").value(0));

        Integer drainActions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operator_action_log WHERE action_type = 'lifecycle_pipeline_parked_drain'",
                Integer.class);
        Assertions.assertEquals(2, drainActions);
    }

    private void insertOutbox(String eventId, OffsetDateTime createdAt, OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (
                    event_id, event_type, event_key, payload_json, created_at, parked_at, attempt_count
                ) VALUES (?, 'UPDATED', ?, '{}', ?, ?, 3)
                """,
                eventId,
                eventId,
                createdAt,
                parkedAt);
    }
}
