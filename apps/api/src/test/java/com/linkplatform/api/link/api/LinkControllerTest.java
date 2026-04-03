package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createLinkReturnsCreatedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "launch-page",
                                  "originalUrl": "https://example.com/launch"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "slug": "launch-page",
                          "originalUrl": "https://example.com/launch"
                        }
                        """))
                .andExpect(jsonPath("$.slug").value("launch-page"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/launch"))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void createLinkWithFutureExpirationPersistsExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "future-link",
                                  "originalUrl": "https://example.com/future",
                                  "expiresAt": "2030-04-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/future-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("future-link"))
                .andExpect(jsonPath("$.expiresAt").value("2030-04-01T08:00:00Z"))
                .andExpect(jsonPath("$.clickTotal").value(0));
    }

    @Test
    void createLinkWithPastExpirationBecomesUnavailableImmediately() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "expired-on-create",
                                  "originalUrl": "https://example.com/expired",
                                  "expiresAt": "2020-04-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/expired-on-create"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: expired-on-create"));
    }

    @Test
    void createLinkRejectsDuplicateSlug() throws Exception {
        String request = """
                {
                  "slug": "repeatable",
                  "originalUrl": "https://example.com/first"
                }
                """;

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "repeatable",
                                  "originalUrl": "https://example.com/second"
                                }
                                """))
                .andExpect(problemDetail(409, "Conflict", "Link slug already exists: repeatable"));
    }

    @Test
    void createLinkRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "bad slug",
                                  "originalUrl": "not-a-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.slug").doesNotExist())
                .andExpect(jsonPath("$.originalUrl").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void createLinkRejectsReservedSlugCaseInsensitively() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "Api",
                                  "originalUrl": "https://example.com/conflict"
                                }
                                """))
                .andExpect(problemDetail(400, "Bad Request", "Link slug is reserved and cannot be used: Api"));
    }

    @Test
    void createLinkRejectsSelfTargetUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "self-loop",
                                  "originalUrl": "http://LOCALHOST:8080/about"
                                }
                                """))
                .andExpect(problemDetail(400, "Bad Request",
                        "Original URL cannot point to the Link Platform itself: http://LOCALHOST:8080/about"));
    }

    @Test
    void getLinkReturnsExistingLink() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "read-me",
                                  "originalUrl": "https://example.com/read-me"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/read-me"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value("read-me"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/read-me"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.clickTotal").value(0));
    }

    @Test
    void getLinkReturnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(get("/api/v1/links/missing-link"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: missing-link"));
    }

    @Test
    void listLinksReturnsRecentLinksInDeterministicOrder() throws Exception {
        insertLink("beta", "https://example.com/beta", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);
        insertLink("alpha", "https://example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);
        insertLink("newest", "https://example.com/newest", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].slug").value("newest"))
                .andExpect(jsonPath("$[1].slug").value("alpha"))
                .andExpect(jsonPath("$[2].slug").value("beta"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].expiresAt").doesNotExist())
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksDefaultsToActiveOnly() throws Exception {
        insertLink(
                "expired-link",
                "https://example.com/expired",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2020-04-01T08:00:00Z"));
        insertLink("active-link", "https://example.com/active", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("active-link"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksHonorsLimit() throws Exception {
        insertLink("one", "https://example.com/one", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);
        insertLink("two", "https://example.com/two", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("two"));
    }

    @Test
    void listLinksExcludesExpiredLinks() throws Exception {
        insertLink(
                "expired-link",
                "https://example.com/expired",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2020-04-01T08:00:00Z"));
        insertLink(
                "active-link",
                "https://example.com/active",
                OffsetDateTime.parse("2026-04-01T09:00:00Z"),
                OffsetDateTime.parse("2030-04-01T08:00:00Z"));

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("active-link"))
                .andExpect(jsonPath("$[0].expiresAt").value("2030-04-01T08:00:00Z"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksFiltersExpiredOnly() throws Exception {
        insertLink("active-link", "https://example.com/active", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertLink(
                "expired-link",
                "https://example.com/expired",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2020-04-01T08:00:00Z"));

        mockMvc.perform(get("/api/v1/links").param("state", "expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("expired-link"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksFiltersAllStates() throws Exception {
        insertLink("active-link", "https://example.com/active", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertLink(
                "expired-link",
                "https://example.com/expired",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2020-04-01T08:00:00Z"));

        mockMvc.perform(get("/api/v1/links").param("state", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("active-link"))
                .andExpect(jsonPath("$[1].slug").value("expired-link"))
                .andExpect(jsonPath("$[0].clickTotal").value(0))
                .andExpect(jsonPath("$[1].clickTotal").value(0));
    }

    @Test
    void listLinksSearchesBySlug() throws Exception {
        insertLink("launch-page", "https://example.com/docs", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertLink("docs-page", "https://example.com/guide", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links").param("q", "launch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("launch-page"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksSearchesByOriginalUrl() throws Exception {
        insertLink("alpha", "https://example.com/launch-docs", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertLink("beta", "https://example.com/other", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links").param("q", "launch-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void listLinksCombinesSearchWithLifecycleFiltering() throws Exception {
        insertLink(
                "launch-expired",
                "https://example.com/launch",
                OffsetDateTime.parse("2026-04-01T09:00:00Z"),
                OffsetDateTime.parse("2020-04-01T08:00:00Z"));
        insertLink("launch-active", "https://example.com/launch", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);

        mockMvc.perform(get("/api/v1/links")
                        .param("q", "launch")
                        .param("state", "expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("launch-expired"))
                .andExpect(jsonPath("$[0].clickTotal").value(0));
    }

    @Test
    void getLinkIncludesClickTotal() throws Exception {
        insertLink("clicked", "https://example.com/clicked", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertClick("clicked", OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        insertClick("clicked", OffsetDateTime.parse("2026-04-01T11:00:00Z"));

        mockMvc.perform(get("/api/v1/links/clicked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("clicked"))
                .andExpect(jsonPath("$.clickTotal").value(2));
    }

    @Test
    void listLinksIncludesClickTotals() throws Exception {
        insertLink("clicked", "https://example.com/clicked", OffsetDateTime.parse("2026-04-01T09:00:00Z"), null);
        insertLink("unclicked", "https://example.com/unclicked", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null);
        insertClick("clicked", OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        insertClick("clicked", OffsetDateTime.parse("2026-04-01T11:00:00Z"));

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("clicked"))
                .andExpect(jsonPath("$[0].clickTotal").value(2))
                .andExpect(jsonPath("$[1].slug").value("unclicked"))
                .andExpect(jsonPath("$[1].clickTotal").value(0));
    }

    @Test
    void listLinksRejectsInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/v1/links").param("limit", "0"))
                .andExpect(problemDetail(400, "Bad Request", "Limit must be between 1 and 100"));
    }

    @Test
    void listLinksRejectsInvalidState() throws Exception {
        mockMvc.perform(get("/api/v1/links").param("state", "unknown"))
                .andExpect(problemDetail(400, "Bad Request", "State must be one of: active, expired, all"));
    }

    @Test
    void updateLinkUpdatesExistingLink() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "editable",
                                  "originalUrl": "https://example.com/original"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/links/editable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("editable"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/updated"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void updateLinkUpdatesExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "expiring",
                                  "originalUrl": "https://example.com/original"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/links/expiring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/original",
                                  "expiresAt": "2030-04-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2030-04-01T08:00:00Z"));
    }

    @Test
    void updateLinkReturnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(put("/api/v1/links/missing-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/updated"
                                }
                                """))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: missing-link"));
    }

    @Test
    void updateLinkRejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "editable",
                                  "originalUrl": "https://example.com/original"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/links/editable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "not-a-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void updateLinkRejectsSelfTargetUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "editable",
                                  "originalUrl": "https://example.com/original"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/links/editable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "http://localhost:8080/about"
                                }
                                """))
                .andExpect(problemDetail(400, "Bad Request",
                        "Original URL cannot point to the Link Platform itself: http://localhost:8080/about"));
    }

    @Test
    void deleteLinkDeletesExistingLink() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "delete-me",
                                  "originalUrl": "https://example.com/delete-me"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/links/delete-me"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/links/delete-me"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: delete-me"));
    }

    @Test
    void deleteLinkReturnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(delete("/api/v1/links/missing-link"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: missing-link"));
    }

    @Test
    void expiredLinkReadReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "expired-read",
                                  "originalUrl": "https://example.com/expired-read",
                                  "expiresAt": "2020-04-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/expired-read"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: expired-read"));
    }

    private void insertLink(String slug, String originalUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO links (slug, original_url, created_at, expires_at) VALUES (?, ?, ?, ?)",
                slug,
                originalUrl,
                createdAt,
                expiresAt);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?)
                """,
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");
        int updated = jdbcTemplate.update(
                """
                UPDATE link_click_daily_rollups
                SET click_count = click_count + 1
                WHERE slug = ? AND rollup_date = ?
                """,
                slug,
                clickedAt.toLocalDate());
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                    VALUES (?, ?, 1)
                    """,
                    slug,
                    clickedAt.toLocalDate());
        }
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
            jsonPath("$.slug").doesNotExist().match(result);
            jsonPath("$.originalUrl").doesNotExist().match(result);
            jsonPath("$.message").doesNotExist().match(result);
        };
    }
}
