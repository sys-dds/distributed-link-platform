package com.linkplatform.api.link.api;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkLifecycleControlsIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private com.linkplatform.api.link.application.LinkReadCache linkReadCache;

    @Test
    void lifecycleTransitionsSupportResumeUnarchiveExpireRestoreAndIdempotency() throws Exception {
        createLink("lifecycle-link");

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"suspend"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "2")
                        .header("Idempotency-Key", "resume-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"resume"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"archive"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "4")
                        .header("Idempotency-Key", "unarchive-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"unarchive"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "5")
                        .header("Idempotency-Key", "expire-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"expire-now"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"extend-expiry","expiresAt":"2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(7));

        mockMvc.perform(delete("/api/v1/links/lifecycle-link")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "7")
                        .header("Idempotency-Key", "delete-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "8")
                        .header("Idempotency-Key", "restore-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"restore"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(9));

        verify(linkReadCache, atLeastOnce()).invalidateOwnerControlPlane(1L);
        verify(linkReadCache, atLeastOnce()).invalidateOwnerAnalytics(1L);
    }

    @Test
    void lifecycleRejectsMissingAndStaleIfMatchAndInvalidTransitions() throws Exception {
        createLink("invalid-lifecycle");

        mockMvc.perform(post("/api/v1/links/invalid-lifecycle/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"resume"}
                                """))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.category").value("missing-if-match"));

        mockMvc.perform(post("/api/v1/links/invalid-lifecycle/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"suspend"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("stale-if-match"));

        mockMvc.perform(post("/api/v1/links/invalid-lifecycle/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"resume"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("bad-request"));
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
