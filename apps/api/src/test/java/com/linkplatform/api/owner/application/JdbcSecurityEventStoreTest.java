package com.linkplatform.api.owner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class JdbcSecurityEventStoreTest {

    @Autowired
    private JdbcSecurityEventStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void queriesOwnerScopedFilteredStablePages() {
        insertEvent("INVALID_CREDENTIAL", 1L, OffsetDateTime.parse("2026-04-06T09:00:00Z"));
        insertEvent("API_KEY_CREATED", 1L, OffsetDateTime.parse("2026-04-06T10:00:00Z"));
        insertEvent("API_KEY_ROTATED", 1L, OffsetDateTime.parse("2026-04-06T10:00:00Z"));
        insertEvent("API_KEY_CREATED", 2L, OffsetDateTime.parse("2026-04-06T11:00:00Z"));

        List<SecurityEventRecord> firstPage = store.findEvents(
                1L,
                new SecurityEventQuery(
                        List.of(SecurityEventType.API_KEY_CREATED, SecurityEventType.API_KEY_ROTATED),
                        OffsetDateTime.parse("2026-04-06T09:30:00Z"),
                        2,
                        null));

        assertEquals(2, firstPage.size());
        assertEquals(SecurityEventType.API_KEY_ROTATED, firstPage.get(0).type());
        assertEquals(SecurityEventType.API_KEY_CREATED, firstPage.get(1).type());
        assertEquals("API key rotated", firstPage.get(0).summary());
        assertNull(firstPage.get(0).metadata());
    }

    private void insertEvent(String type, Long ownerId, OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_security_events (
                    event_type, owner_id, api_key_hash, request_method, request_path, remote_address, detail_summary, occurred_at
                ) VALUES (?, ?, 'hash', 'GET', '/secret', 'hash-ip', 'secret-value', ?)
                """,
                type,
                ownerId,
                occurredAt);
    }
}
