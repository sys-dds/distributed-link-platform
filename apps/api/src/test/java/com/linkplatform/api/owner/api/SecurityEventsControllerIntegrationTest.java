package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SecurityEventsControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";
    private static final String PRO_API_KEY = "pro-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerReadsOnlyOwnEventsWithFiltersAndSafeSummaries() throws Exception {
        insertEvent("INVALID_CREDENTIAL", 1L, "token=abc123", OffsetDateTime.parse("2026-04-06T09:00:00Z"));
        insertEvent("API_KEY_CREATED", 1L, "apikey-secret", OffsetDateTime.parse("2026-04-06T10:00:00Z"));
        insertEvent("INVALID_CREDENTIAL", 2L, "other-owner", OffsetDateTime.parse("2026-04-06T11:00:00Z"));

        mockMvc.perform(get("/api/v1/owner/security-events")
                        .header("X-API-Key", FREE_API_KEY)
                        .param("type", "api_key_created,invalid_credential")
                        .param("since", "2026-04-06T08:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("api_key_created"))
                .andExpect(jsonPath("$.items[0].summary").value("API key created"))
                .andExpect(jsonPath("$.items[1].type").value("invalid_credential"))
                .andExpect(jsonPath("$.items[1].summary").value("Invalid credential rejected"))
                .andExpect(jsonPath("$.items[0].metadata").doesNotExist())
                .andExpect(jsonPath("$.items[0].detail").doesNotExist());

        mockMvc.perform(get("/api/v1/owner/security-events").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].summary").value("Invalid credential rejected"));
    }

    @Test
    void cursorPaginationIsStable() throws Exception {
        insertEvent("API_KEY_CREATED", 1L, "first", OffsetDateTime.parse("2026-04-06T12:00:00Z"));
        insertEvent("API_KEY_ROTATED", 1L, "second", OffsetDateTime.parse("2026-04-06T12:00:00Z"));
        insertEvent("API_KEY_REVOKED", 1L, "third", OffsetDateTime.parse("2026-04-06T11:00:00Z"));

        String firstPage = mockMvc.perform(get("/api/v1/owner/security-events")
                        .header("X-API-Key", FREE_API_KEY)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String nextCursor = objectMapper.readTree(firstPage).get("nextCursor").asText();

                mockMvc.perform(get("/api/v1/owner/security-events")
                        .header("X-API-Key", FREE_API_KEY)
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("api_key_revoked"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    private void insertEvent(String type, Long ownerId, String detailSummary, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_security_events (
                    event_type, owner_id, api_key_hash, request_method, request_path, remote_address, detail_summary, occurred_at
                ) VALUES (?, ?, 'hash', 'GET', '/secret', 'hashed-ip', ?, ?)
                """,
                type,
                ownerId,
                detailSummary,
                occurredAt);
    }
}
