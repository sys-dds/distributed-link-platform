package com.linkplatform.api.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linkplatform.api.link.application.PipelineControlStore;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PipelineHealthIndicatorParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Test
    void exposesAllParityFieldsForHealthIndicator() throws Exception {
        OffsetDateTime pauseTime = OffsetDateTime.parse("2026-04-06T08:20:00Z");
        
        // Setup analytics pipeline state
        pipelineControlStore.pause("analytics", "Database migration required", pauseTime);
        pipelineControlStore.recordForceTick("analytics", OffsetDateTime.parse("2026-04-06T08:21:00Z"));
        pipelineControlStore.recordRequeue("analytics", OffsetDateTime.parse("2026-04-06T08:22:00Z"));
        pipelineControlStore.recordRelayFailure("analytics", OffsetDateTime.parse("2026-04-06T08:23:00Z"), "Deadlock detected");

        // Setup lifecycle pipeline state
        pipelineControlStore.pause("lifecycle", "Manual intervention", pauseTime);
        pipelineControlStore.recordForceTick("lifecycle", OffsetDateTime.parse("2026-04-06T08:24:00Z"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.pipeline.details.analytics.paused").value(true))
                .andExpect(jsonPath("$.components.pipeline.details.analytics.pauseReason").value("Database migration required"))
                .andExpect(jsonPath("$.components.pipeline.details.analytics.lastForceTickAt").isNotEmpty())
                .andExpect(jsonPath("$.components.pipeline.details.analytics.lastRequeueAt").isNotEmpty())
                .andExpect(jsonPath("$.components.pipeline.details.analytics.lastRelayFailureAt").isNotEmpty())
                .andExpect(jsonPath("$.components.pipeline.details.analytics.lastRelayFailureReason").value("Deadlock detected"))
                
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.paused").value(true))
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.pauseReason").value("Manual intervention"))
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.lastForceTickAt").isNotEmpty());
    }
}
