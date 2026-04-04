package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkRedirectControllerTest {

    private static final String FREE_API_KEY = "dev-free-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redirectReturnsTemporaryRedirectForKnownSlug() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "launch-page",
                                  "originalUrl": "https://example.com/launch"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/launch-page")
                        .header("User-Agent", "test-agent")
                        .header("Referer", "https://referrer.example"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/launch"))
                .andExpect(content().string(""));

        assertOutboxCount("launch-page", 1);
        assertClickCount("launch-page", 0);
    }

    @Test
    void redirectReturnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(get("/missing-link"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: missing-link"));

        assertTotalOutboxRows(0);
    }

    @Test
    void deletedLinkNoLongerRedirects() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "gone-link",
                                  "originalUrl": "https://example.com/gone"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/links/gone-link")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gone-link"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: gone-link"));
    }

    @Test
    void expiredLinkNoLongerRedirects() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "expired-link",
                                  "originalUrl": "https://example.com/expired",
                                  "expiresAt": "2020-04-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/expired-link"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: expired-link"));

        assertOutboxCount("expired-link", 0);
        assertClickCount("expired-link", 0);
    }

    private void assertClickCount(String slug, int expectedCount) {
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_clicks WHERE slug = ?",
                Integer.class,
                slug);
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, actualCount);
    }

    private void assertOutboxCount(String slug, int expectedCount) {
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE event_key = ?",
                Integer.class,
                slug);
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, actualCount);
    }

    private void assertTotalOutboxRows(int expectedCount) {
        Integer actualCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_outbox", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, actualCount);
    }

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(
            int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
            jsonPath("$.message").doesNotExist().match(result);
        };
    }
}
