package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkplatform.api.link.domain.Link;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgresLinkPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.8")
            .withDatabaseName("link_platform_test")
            .withUsername("link_platform")
            .withPassword("link_platform");

    @Autowired
    private LinkApplicationService linkApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearLinksTable() {
        jdbcTemplate.update("DELETE FROM links");
    }

    @Test
    void createAndResolveUsePostgresStorage() {
        Link createdLink = linkApplicationService.createLink(
                new CreateLinkCommand("persistent-link", "https://example.com/persistent", null, null, null));

        Link resolvedLink = linkApplicationService.resolveLink("persistent-link");
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM links WHERE slug = ?",
                Integer.class,
                "persistent-link");

        assertEquals("persistent-link", createdLink.slug().value());
        assertEquals("https://example.com/persistent", resolvedLink.originalUrl().value());
        assertEquals(1, rowCount);
    }

    @Test
    void duplicateSlugIsRejectedAgainstPostgres() {
        linkApplicationService.createLink(new CreateLinkCommand("repeatable", "https://example.com/one", null, null, null));

        assertThrows(
                DuplicateLinkSlugException.class,
                () -> linkApplicationService.createLink(new CreateLinkCommand(
                        "repeatable", "https://example.com/two", null, null, null)));

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM links WHERE slug = ?",
                Integer.class,
                "repeatable");

        assertEquals(1, rowCount);
    }
}
