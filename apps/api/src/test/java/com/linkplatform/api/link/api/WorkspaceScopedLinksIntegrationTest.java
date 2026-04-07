package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class WorkspaceScopedLinksIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";
    private static final String PRO_API_KEY = "pro-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void personalWorkspaceFallbackKeepsExistingSingleOwnerFlowWorking() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"workspace-fallback","originalUrl":"https://example.com/fallback"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/workspace-fallback").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("workspace-fallback"))
                .andExpect(jsonPath("$.abuseStatus").value("active"));
    }

    @Test
    void linksDoNotLeakAcrossWorkspaces() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"personal-only","originalUrl":"https://example.com/personal-only"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/links/personal-only").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isNotFound());
    }
}
