package com.linkplatform.api.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PostgresLinkStoreRoutingTest {

    @Test
    void ownerDetailReadsUseQueryJdbcTemplatePath() {
        JdbcTemplate writeJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JdbcTemplate queryJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        PostgresLinkStore store = new PostgresLinkStore(writeJdbcTemplate, queryJdbcTemplate, new ObjectMapper());
        OffsetDateTime now = OffsetDateTime.parse("2026-04-06T09:00:00Z");
        LinkDetails expected = new LinkDetails(
                "route-link",
                "https://example.com/route",
                now.minusDays(1),
                null,
                "Route",
                List.of("docs"),
                "example.com",
                LinkAbuseStatus.ACTIVE,
                2L,
                4L);

        when(queryJdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<LinkDetails>>any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(List.of(expected));

        assertThat(store.findDetailsBySlug("route-link", now, 1L)).contains(expected);
        verify(queryJdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<LinkDetails>>any(),
                any(),
                any(),
                any());
        verifyNoInteractions(writeJdbcTemplate);
    }

    @Test
    void analyticsReadsUseQueryJdbcTemplatePath() {
        JdbcTemplate writeJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JdbcTemplate queryJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        PostgresLinkStore store = new PostgresLinkStore(writeJdbcTemplate, queryJdbcTemplate, new ObjectMapper());
        OffsetDateTime now = OffsetDateTime.parse("2026-04-06T09:00:00Z");
        TopLinkTraffic expected = new TopLinkTraffic("alpha", "https://example.com/alpha", 5L);

        when(queryJdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<TopLinkTraffic>>any(),
                        any(),
                        any()))
                .thenReturn(List.of(expected));

        assertThat(store.findTopLinks(LinkTrafficWindow.LAST_24_HOURS, now, 1L)).containsExactly(expected);
        verify(queryJdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TopLinkTraffic>>any(),
                any(),
                any());
        verifyNoInteractions(writeJdbcTemplate);
    }

    @Test
    void writesStayOnPrimaryJdbcTemplatePath() {
        JdbcTemplate writeJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        JdbcTemplate queryJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        PostgresLinkStore store = new PostgresLinkStore(writeJdbcTemplate, queryJdbcTemplate, new ObjectMapper());
        when(writeJdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        boolean saved = store.save(
                new Link(new LinkSlug("write-link"), new OriginalUrl("https://example.com/write")),
                null,
                "Write",
                List.of("docs"),
                "example.com",
                1L,
                1L);

        assertThat(saved).isTrue();
        verify(writeJdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(queryJdbcTemplate);
    }
}
