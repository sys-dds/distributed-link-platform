package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class OwnerApiKeysControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createRotateListAndRevokeApiKeys() throws Exception {
        String createdJson = mockMvc.perform(post("/api/v1/owner/api-keys")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"ci-agent"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey.label").value("ci-agent"))
                .andExpect(jsonPath("$.plaintextKey").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String createdKey = objectMapper.readTree(createdJson).get("plaintextKey").asText();
        long keyId = objectMapper.readTree(createdJson).get("apiKey").get("id").asLong();

        mockMvc.perform(get("/api/v1/me").header("X-API-Key", createdKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerKey").value("free-owner"));

        String rotatedJson = mockMvc.perform(post("/api/v1/owner/api-keys/{keyId}/rotate", keyId)
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintextKey").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rotatedKey = objectMapper.readTree(rotatedJson).get("plaintextKey").asText();

        mockMvc.perform(get("/api/v1/me").header("X-API-Key", createdKey))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header("X-API-Key", rotatedKey))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/owner/api-keys").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyPrefix").exists());

        long rotatedId = objectMapper.readTree(rotatedJson).get("apiKey").get("id").asLong();
        mockMvc.perform(delete("/api/v1/owner/api-keys/{keyId}", rotatedId).header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("X-API-Key", rotatedKey))
                .andExpect(status().isUnauthorized());
    }
}
