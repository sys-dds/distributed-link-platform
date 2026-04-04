package com.linkplatform.api.projection;

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
class ProjectionJobsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createGetAndListProjectionJobs() throws Exception {
        mockMvc.perform(post("/api/v1/projection-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobType": "ACTIVITY_FEED_REPLAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobType").value("ACTIVITY_FEED_REPLAY"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/v1/projection-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobType").value("ACTIVITY_FEED_REPLAY"));

        mockMvc.perform(get("/api/v1/projection-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }
}
