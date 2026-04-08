package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "link-platform.webhooks.allow-private-callback-hosts=false",
        "link-platform.webhooks.allow-http-callbacks=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhooksControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Test
    void privateHttpCallbacksAreRejectedWhenExplicitAllowancesAreDisabled() throws Exception {
        String apiKey = bootstrapPersonalWorkspaceApiKey("strict-webhooks-key", "[\"webhooks:write\",\"webhooks:read\"]");
        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"strict","callbackUrl":"http://127.0.0.1:8080/hook","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void privateHttpsCallbacksAreAlsoRejectedWhenHostAllowanceIsDisabled() throws Exception {
        String apiKey = bootstrapPersonalWorkspaceApiKey("strict-webhooks-private-key", "[\"webhooks:write\"]");
        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"strict-private","callbackUrl":"https://127.0.0.1:8080/hook","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Webhook callbackUrl must not target localhost or private addresses"));
    }

    @Test
    void allowFlagsStayExplicitInTestAndDockerConfigs() {
        var testFactory = new YamlPropertiesFactoryBean();
        testFactory.setResources(new ClassPathResource("application-test.yml"));
        var test = testFactory.getObject();

        org.assertj.core.api.Assertions.assertThat(test).isNotNull();
        org.assertj.core.api.Assertions.assertThat(test.getProperty("link-platform.webhooks.allow-private-callback-hosts"))
                .isEqualTo("false");
        org.assertj.core.api.Assertions.assertThat(test.getProperty("link-platform.webhooks.allow-http-callbacks"))
                .isEqualTo("false");
    }

    @Test
    void applicationAndDockerConfigsKeepInternalWebhookAllowancesExplicit() {
        var baseFactory = new YamlPropertiesFactoryBean();
        baseFactory.setResources(new ClassPathResource("application.yml"));
        var base = baseFactory.getObject();

        var dockerFactory = new YamlPropertiesFactoryBean();
        dockerFactory.setResources(new ClassPathResource("application-docker.yml"));
        var docker = dockerFactory.getObject();

        org.assertj.core.api.Assertions.assertThat(base)
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(base.getProperty("link-platform.webhooks.allow-private-callback-hosts"))
                .isEqualTo("${LINK_PLATFORM_WEBHOOKS_ALLOW_PRIVATE_CALLBACK_HOSTS:false}");
        org.assertj.core.api.Assertions.assertThat(base.getProperty("link-platform.webhooks.allow-http-callbacks"))
                .isEqualTo("${LINK_PLATFORM_WEBHOOKS_ALLOW_HTTP_CALLBACKS:false}");
        org.assertj.core.api.Assertions.assertThat(docker)
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(docker.getProperty("link-platform.webhooks.allow-private-callback-hosts"))
                .isEqualTo("true");
        org.assertj.core.api.Assertions.assertThat(docker.getProperty("link-platform.webhooks.allow-http-callbacks"))
                .isEqualTo("true");
    }

    private String bootstrapPersonalWorkspaceApiKey(String plaintextKey, String scopesJson) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = 1",
                Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (1, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                """,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                scopesJson,
                OffsetDateTime.now());
        return plaintextKey;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
