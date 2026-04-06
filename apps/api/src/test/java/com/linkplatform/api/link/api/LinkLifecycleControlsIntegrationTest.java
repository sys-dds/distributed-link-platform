package com.linkplatform.api.link.api;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void lifecycleTransitionsEnforceLegalMovesAndInvalidateCaches() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"lifecycle-link","originalUrl":"https://example.com/lifecycle"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"suspend"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/lifecycle-link")).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"archive"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(post("/api/v1/links/lifecycle-link/lifecycle")
                        .header("X-API-Key", FREE_API_KEY)
                        .header("If-Match", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"suspend"}
                                """))
                .andExpect(status().isBadRequest());

        verify(linkReadCache, atLeastOnce()).invalidateOwnerControlPlane(1L);
        verify(linkReadCache, atLeastOnce()).invalidateOwnerAnalytics(1L);
    }
}
