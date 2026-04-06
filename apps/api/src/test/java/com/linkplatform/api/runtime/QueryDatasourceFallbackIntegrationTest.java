package com.linkplatform.api.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
        "link-platform.query.datasource.driver-class-name=org.h2.Driver",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class QueryDatasourceFallbackIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void ownerReadsFallbackToPrimaryWhenDedicatedQueryDatasourceIsUnavailable() throws Exception {
        insertOwnedLink("fallback-link", "https://example.com/fallback");

        mockMvc.perform(get("/api/v1/links/fallback-link").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("fallback-link"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/fallback"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.runtimeRole.details.mode").value("ALL"))
                .andExpect(jsonPath("$.components.queryDataSource.details.dedicatedConfigured").value(true))
                .andExpect(jsonPath("$.components.queryDataSource.details.route").value("primary-fallback"))
                .andExpect(jsonPath("$.components.queryDataSource.details.dedicatedAvailable").value(false));

        assertTrue(meterRegistry.get("link.query.datasource.fallback").counter().count() >= 1.0d);
    }

    private void insertOwnedLink(String slug, String originalUrl) {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-06T08:00:00Z");
        String hostname = java.net.URI.create(originalUrl).getHost().toLowerCase();
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, 1, 1)
                """,
                slug,
                originalUrl,
                createdAt,
                hostname);
        jdbcTemplate.update(
                """
                INSERT INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, deleted_at, version, owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1, 1)
                """,
                slug,
                originalUrl,
                createdAt,
                createdAt,
                "Fallback",
                "[\"docs\"]",
                hostname,
                null);
    }
}
