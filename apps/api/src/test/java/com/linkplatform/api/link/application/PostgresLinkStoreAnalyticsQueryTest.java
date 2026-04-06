package com.linkplatform.api.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresLinkStoreAnalyticsQueryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.8")
            .withDatabaseName("link_platform_test")
            .withUsername("link_platform")
            .withPassword("link_platform");

    private static JdbcTemplate jdbcTemplate;
    private static PostgresLinkStore store;

    @BeforeAll
    static void setUpStore() {
        DataSource dataSource = dataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new PostgresLinkStore(jdbcTemplate, jdbcTemplate, new ObjectMapper());
        createSchema();
    }

    @AfterAll
    static void tearDownStore() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM link_activity_events");
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
        jdbcTemplate.update("DELETE FROM link_clicks");
        jdbcTemplate.update("DELETE FROM link_catalog_projection");
        jdbcTemplate.update("DELETE FROM links");
    }

    @Test
    void rangeAggregationReturnsOrderedBucketsAndCorrectTotals() {
        OffsetDateTime from = OffsetDateTime.parse("2026-04-06T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-06T04:00:00Z");
        insertLink("series-a", 1L, "ACTIVE", "team");
        insertClick("series-a", from.plusMinutes(5));
        insertClick("series-a", from.plusHours(2).plusMinutes(10));

        assertThat(store.findTrafficSeries("series-a", from, to, "hour", 1L))
                .extracting(LinkTrafficSeriesBucket::bucketStart, LinkTrafficSeriesBucket::clickTotal)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(from, 1L),
                        org.assertj.core.groups.Tuple.tuple(from.plusHours(2), 1L));
        assertThat(store.countClicksForSlugInRange("series-a", from, to, 1L)).isEqualTo(2L);
    }

    @Test
    void filteredTopAndTrendingQueriesRespectOwnerTagLifecycleAndRealCounts() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-06T09:00:00Z").truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime from = now.minusHours(6);
        OffsetDateTime to = now.minusHours(3);
        insertLink("match-a", 1L, "ACTIVE", "team");
        insertLink("match-b", 1L, "ACTIVE", "team");
        insertLink("archived", 1L, "ARCHIVED", "team");
        insertLink("other-owner", 2L, "ACTIVE", "team");

        insertClick("match-a", from.plusMinutes(10));
        insertClick("match-a", from.plusMinutes(20));
        insertClick("match-a", from.minusHours(3).plusMinutes(5));
        insertClick("match-b", from.plusMinutes(30));
        insertClick("archived", from.plusMinutes(15));
        insertClick("other-owner", from.plusMinutes(15));

        assertThat(store.findTopLinks(from, to, 10, "team", LinkLifecycleState.ACTIVE, now, 1L))
                .extracting(TopLinkTraffic::slug, TopLinkTraffic::clickTotal)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("match-a", 2L),
                        org.assertj.core.groups.Tuple.tuple("match-b", 1L));

        assertThat(store.findTrendingLinks(from, to, 10, "team", LinkLifecycleState.ACTIVE, now, 1L))
                .extracting(TrendingLink::slug, TrendingLink::currentWindowClicks, TrendingLink::previousWindowClicks, TrendingLink::clickGrowth)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("match-a", 2L, 1L, 1L),
                        org.assertj.core.groups.Tuple.tuple("match-b", 1L, 0L, 1L));
    }

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE links (
                    slug VARCHAR(64) PRIMARY KEY,
                    original_url VARCHAR(2048) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    title VARCHAR(255),
                    tags_json TEXT,
                    hostname VARCHAR(255),
                    version BIGINT NOT NULL,
                    owner_id BIGINT NOT NULL,
                    lifecycle_state VARCHAR(32) NOT NULL,
                    expires_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE link_catalog_projection (
                    slug VARCHAR(100) PRIMARY KEY,
                    original_url TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL,
                    title VARCHAR(255),
                    tags_json TEXT,
                    hostname VARCHAR(255),
                    expires_at TIMESTAMPTZ,
                    lifecycle_state VARCHAR(32) NOT NULL,
                    deleted_at TIMESTAMPTZ,
                    version BIGINT NOT NULL,
                    owner_id BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE link_clicks (
                    id BIGSERIAL PRIMARY KEY,
                    event_id VARCHAR(255) NOT NULL,
                    slug VARCHAR(64) NOT NULL,
                    clicked_at TIMESTAMPTZ NOT NULL,
                    user_agent VARCHAR(1024),
                    referrer VARCHAR(2048),
                    remote_address VARCHAR(128)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE link_click_daily_rollups (
                    slug VARCHAR(64) NOT NULL,
                    rollup_date DATE NOT NULL,
                    click_count BIGINT NOT NULL,
                    PRIMARY KEY (slug, rollup_date)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE link_activity_events (
                    id BIGSERIAL PRIMARY KEY,
                    event_id VARCHAR(255) NOT NULL,
                    owner_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    slug VARCHAR(64) NOT NULL,
                    original_url VARCHAR(2048) NOT NULL,
                    title VARCHAR(255),
                    tags_json TEXT,
                    hostname VARCHAR(255),
                    expires_at TIMESTAMPTZ,
                    occurred_at TIMESTAMPTZ NOT NULL
                )
                """);
    }

    private void insertLink(String slug, long ownerId, String lifecycleState, String tag) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, title, tags_json, hostname, version, owner_id, lifecycle_state, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                "https://example.com/" + slug,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                slug,
                "[\"" + tag + "\"]",
                "example.com",
                1L,
                ownerId,
                lifecycleState,
                null);
        jdbcTemplate.update(
                """
                INSERT INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, lifecycle_state, deleted_at, version, owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                "https://example.com/" + slug,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                slug,
                "[\"" + tag + "\"]",
                "example.com",
                null,
                lifecycleState,
                null,
                1L,
                ownerId);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + clickedAt.toInstant().toEpochMilli(),
                slug,
                clickedAt,
                "test-agent",
                null,
                "127.0.0.1");
    }
}
