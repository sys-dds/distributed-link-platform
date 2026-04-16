package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ConcurrentDuplicateCreateIntegrationTest {

    private static final String API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateCreatesWithSameIdempotencyKeyProduceOneLogicalCreateAndOneOutboxEvent() throws Exception {
        int requestCount = 2;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
            futures.add(executorService.submit(createRequest(ready, start)));
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

        for (MvcResult result : results) {
            assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            assertEquals(
                    "{\"slug\":\"dupe-create\",\"originalUrl\":\"https://example.com/dupe-create\",\"version\":1}",
                    result.getResponse().getContentAsString());
        }

        assertEquals(1L, count("SELECT COUNT(*) FROM links WHERE slug = 'dupe-create'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_mutation_idempotency WHERE idempotency_key = '1:dupe-create-key'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_lifecycle_outbox WHERE event_type = 'CREATED' AND event_key = 'dupe-create'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_activity_events WHERE event_type = 'CREATED' AND slug = 'dupe-create'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_catalog_projection WHERE slug = 'dupe-create'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_discovery_projection WHERE slug = 'dupe-create'"));
    }

    private Callable<MvcResult> createRequest(CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return mockMvc.perform(post("/api/v1/links")
                            .header("X-API-Key", API_KEY)
                            .header("Idempotency-Key", "dupe-create-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"slug":"dupe-create","originalUrl":"https://example.com/dupe-create"}
                                    """))
                    .andReturn();
        };
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
