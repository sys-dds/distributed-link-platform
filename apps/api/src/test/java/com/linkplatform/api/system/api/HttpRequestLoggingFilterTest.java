package com.linkplatform.api.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HttpRequestLoggingFilterTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createLinkLogsStructuredRequestLine(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "logged-link",
                                  "originalUrl": "https://example.com/logged"
                                }
                                """))
                .andExpect(status().isCreated());

        assertThat(output.getOut()).contains("http_request method=POST path=/api/v1/links status=201 duration_ms=");
        assertThat(output.getOut()).contains("api_key=[REDACTED]");
        assertThat(output.getOut()).doesNotContain(FREE_API_KEY);
    }

    @Test
    void redirectLogsStructuredRequestLine(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "logged-redirect",
                                  "originalUrl": "https://example.com/redirected"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/logged-redirect"))
                .andExpect(status().isTemporaryRedirect());

        assertThat(output.getOut()).contains("http_request method=GET path=/logged-redirect status=307 duration_ms=");
    }
}
