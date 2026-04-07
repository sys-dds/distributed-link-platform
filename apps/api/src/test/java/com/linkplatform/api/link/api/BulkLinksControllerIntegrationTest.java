package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BulkLinksControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bulkActionsReturnPerItemBestEffortResultsAndRespectIdempotency() throws Exception {
        createLink("bulk-a");
        createLink("bulk-b");

        mockMvc.perform(post("/api/v1/links/bulk/actions")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("Idempotency-Key", "bulk-archive-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"archive","slugs":["bulk-a","missing-link","bulk-b"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].slug").value("bulk-a"))
                .andExpect(jsonPath("$.items[0].success").value(true))
                .andExpect(jsonPath("$.items[1].slug").value("missing-link"))
                .andExpect(jsonPath("$.items[1].success").value(false))
                .andExpect(jsonPath("$.items[1].errorCategory").value("not-found"));

        mockMvc.perform(post("/api/v1/links/bulk/actions")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("Idempotency-Key", "bulk-archive-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"archive","slugs":["bulk-a","missing-link","bulk-b"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].success").value(true))
                .andExpect(jsonPath("$.items[2].success").value(true));
    }

    @Test
    void bulkUpdateTagsAndExpiryValidateRequestAndBatchSize() throws Exception {
        createLink("bulk-tags");

        mockMvc.perform(post("/api/v1/links/bulk/actions")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("Idempotency-Key", "bulk-tags-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"update-tags","slugs":["bulk-tags"],"tags":["docs","ops"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].success").value(true))
                .andExpect(jsonPath("$.items[0].newVersion").value(2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/links/bulk-tags")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abuseStatus").value("active"));

        mockMvc.perform(post("/api/v1/links/bulk/actions")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("Idempotency-Key", "bulk-expiry-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"update-expiry","slugs":["bulk-tags"],"expiresAt":"2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].success").value(true));

        mockMvc.perform(post("/api/v1/links/bulk/actions")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("Idempotency-Key", "bulk-invalid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"update-tags","slugs":[],"tags":["docs"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void createLink(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","originalUrl":"https://example.com/%s"}
                                """.formatted(slug, slug)))
                .andExpect(status().isCreated());
    }
}
