package com.linkplatform.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "link-platform.query.datasource.url=jdbc:h2:tcp://localhost:65534/linkplatform",
        "link-platform.query.datasource.username=query_user",
        "link-platform.query.datasource.driver-class-name=org.h2.Driver",
        "link-platform.query-replica.enabled=true",
        "link-platform.query-replica.max-lag-seconds=30",
        "link-platform.query-replica.fallback-log-enabled=true",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class QueryReplicaFallbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleReplicaFallsBackToPrimaryAndRecordsRuntimeState() throws Exception {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'free-owner'", Long.class);
        jdbcTemplate.update(
                """
                UPDATE query_replica_runtime_state
                SET enabled = TRUE,
                    lag_seconds = 120,
                    last_replica_visible_event_at = ?,
                    updated_at = ?
                WHERE replica_name = 'primary-query-replica'
                """,
                OffsetDateTime.parse("2026-04-08T08:00:00Z"),
                OffsetDateTime.parse("2026-04-08T08:02:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, workspace_id, lifecycle_state, abuse_status)
                VALUES ('replica-fallback-link', 'https://example.com/replica', ?, 'example.com', 1, 1, ?, 'ACTIVE', 'ACTIVE')
                """,
                OffsetDateTime.now(),
                workspaceId);

        mockMvc.perform(get("/api/v1/links/replica-fallback-link").header("X-API-Key", "free-owner-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("replica-fallback-link"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.queryDataSource.details.replicaEnabled").value(true))
                .andExpect(jsonPath("$.components.queryDataSource.details.lagSeconds").value(120))
                .andExpect(jsonPath("$.components.queryDataSource.details.fallbackActive").value(true));

        mockMvc.perform(get("/api/v1/ops/status").header("X-API-Key", "free-owner-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryRuntime.replicaEnabled").value(true))
                .andExpect(jsonPath("$.queryRuntime.lagSeconds").value(120))
                .andExpect(jsonPath("$.queryRuntime.fallbackActive").value(true));

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM query_replica_fallback_log WHERE replica_name = 'primary-query-replica'",
                Integer.class);
        assertThat(logCount).isGreaterThanOrEqualTo(1);
    }
}
