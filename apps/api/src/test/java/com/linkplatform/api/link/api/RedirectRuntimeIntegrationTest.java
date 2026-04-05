package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest(properties = "link-platform.runtime.mode=redirect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectRuntimeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redirectRuntimeServesPublicRedirectAndKeepsAsyncWriteShape() throws Exception {
        insertLink("runtime-link", "https://example.com/runtime");

        mockMvc.perform(get("/runtime-link"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/runtime"));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox WHERE event_key = 'runtime-link'",
                        Integer.class));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_clicks WHERE slug = 'runtime-link'",
                        Integer.class));
    }

    @Test
    void redirectRuntimeDoesNotExposeOwnerControlPlaneSurface() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isNotFound());
    }

    private void insertLink(String slug, String originalUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, 1, 1)
                """,
                slug,
                originalUrl,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                java.net.URI.create(originalUrl).getHost().toLowerCase());
    }
}
