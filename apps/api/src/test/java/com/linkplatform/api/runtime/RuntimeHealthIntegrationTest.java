package com.linkplatform.api.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessShowsRoleAwareRuntimeAndQueryPosture() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.runtimeRole.details.mode").value("ALL"))
                .andExpect(jsonPath("$.components.runtimeRole.details.httpEnabled").value(true))
                .andExpect(jsonPath("$.components.runtimeRole.details.redirectEnabled").value(true))
                .andExpect(jsonPath("$.components.runtimeRole.details.controlPlaneEnabled").value(true))
                .andExpect(jsonPath("$.components.runtimeRole.details.workerEnabled").value(true))
                .andExpect(jsonPath("$.components.redirectRuntime.details.required").value(true))
                .andExpect(jsonPath("$.components.redirectRuntime.details.region").value("local"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverConfigured").value(false))
                .andExpect(jsonPath("$.components.redirectRuntime.details.routeStrategy").value("cache-first-primary-lookup"))
                .andExpect(jsonPath("$.components.queryDataSource.details.required").value(true))
                .andExpect(jsonPath("$.components.queryDataSource.details.usingPrimaryByDefault").value(true))
                .andExpect(jsonPath("$.components.queryDataSource.details.route").value("primary"));
    }
}
