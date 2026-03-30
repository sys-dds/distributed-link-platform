package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createLinkReturnsCreatedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "launch-page",
                                  "originalUrl": "https://example.com/launch"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "slug": "launch-page",
                          "originalUrl": "https://example.com/launch"
                        }
                        """))
                .andExpect(jsonPath("$.slug").value("launch-page"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/launch"))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void createLinkRejectsDuplicateSlug() throws Exception {
        String request = """
                {
                  "slug": "repeatable",
                  "originalUrl": "https://example.com/first"
                }
                """;

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "repeatable",
                                  "originalUrl": "https://example.com/second"
                                }
                                """))
                .andExpect(problemDetail(409, "Conflict", "Link slug already exists: repeatable"));
    }

    @Test
    void createLinkRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "bad slug",
                                  "originalUrl": "not-a-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.slug").doesNotExist())
                .andExpect(jsonPath("$.originalUrl").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void createLinkRejectsReservedSlugCaseInsensitively() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "Api",
                                  "originalUrl": "https://example.com/conflict"
                                }
                                """))
                .andExpect(problemDetail(400, "Bad Request", "Link slug is reserved and cannot be used: Api"));
    }

    @Test
    void createLinkRejectsSelfTargetUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "self-loop",
                                  "originalUrl": "http://LOCALHOST:8080/about"
                                }
                                """))
                .andExpect(problemDetail(400, "Bad Request",
                        "Original URL cannot point to the Link Platform itself: http://LOCALHOST:8080/about"));
    }

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(
            int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
            jsonPath("$.slug").doesNotExist().match(result);
            jsonPath("$.originalUrl").doesNotExist().match(result);
            jsonPath("$.message").doesNotExist().match(result);
        };
    }
}
