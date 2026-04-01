package com.linkplatform.api.system.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthProbeTest {

    private static final MediaType ACTUATOR_JSON =
            MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generalHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.groups[0]").value("liveness"))
                .andExpect(jsonPath("$.groups[1]").value("readiness"));
    }

    @Test
    void livenessEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsEndpointReturnsAvailableMeters() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ACTUATOR_JSON))
                .andExpect(jsonPath("$.names", hasItem("jvm.memory.used")))
                .andExpect(jsonPath("$.names", hasItem("http.server.requests")));
    }
}
