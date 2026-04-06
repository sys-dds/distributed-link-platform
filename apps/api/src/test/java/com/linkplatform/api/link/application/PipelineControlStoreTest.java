package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PipelineControlStoreTest {

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Test
    void seededRowsExistAndPauseResumePersistAcrossReads() {
        PipelineControl analytics = pipelineControlStore.get("analytics");
        PipelineControl lifecycle = pipelineControlStore.get("lifecycle");

        assertEquals("analytics", analytics.pipelineName());
        assertEquals("lifecycle", lifecycle.pipelineName());
        assertFalse(analytics.paused());
        assertFalse(lifecycle.paused());

        pipelineControlStore.pause("analytics", "maintenance window", OffsetDateTime.parse("2026-04-06T10:00:00Z"));
        PipelineControl paused = pipelineControlStore.get("analytics");
        assertTrue(paused.paused());
        assertEquals("maintenance window", paused.pauseReason());

        pipelineControlStore.resume("analytics", OffsetDateTime.parse("2026-04-06T10:05:00Z"));
        PipelineControl resumed = pipelineControlStore.get("analytics");
        assertFalse(resumed.paused());
        assertNull(resumed.pauseReason());
    }

    @Test
    void successFailureRequeueAndForceTickTimestampsPersist() {
        pipelineControlStore.recordForceTick("analytics", OffsetDateTime.parse("2026-04-06T09:00:00Z"));
        pipelineControlStore.recordRequeue("analytics", OffsetDateTime.parse("2026-04-06T09:05:00Z"));
        pipelineControlStore.recordRelaySuccess("analytics", OffsetDateTime.parse("2026-04-06T09:10:00Z"));
        pipelineControlStore.recordRelayFailure(
                "analytics",
                OffsetDateTime.parse("2026-04-06T09:15:00Z"),
                "x".repeat(700));

        PipelineControl control = pipelineControlStore.get("analytics");
        assertEquals(OffsetDateTime.parse("2026-04-06T09:00:00Z"), control.lastForceTickAt());
        assertEquals(OffsetDateTime.parse("2026-04-06T09:05:00Z"), control.lastRequeueAt());
        assertEquals(OffsetDateTime.parse("2026-04-06T09:10:00Z"), control.lastRelaySuccessAt());
        assertEquals(OffsetDateTime.parse("2026-04-06T09:15:00Z"), control.lastRelayFailureAt());
        assertNotNull(control.lastRelayFailureReason());
        assertEquals(512, control.lastRelayFailureReason().length());
    }
}
