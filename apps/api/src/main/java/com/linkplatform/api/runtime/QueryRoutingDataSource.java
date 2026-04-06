package com.linkplatform.api.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

public class QueryRoutingDataSource extends AbstractDataSource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(QueryRoutingDataSource.class);

    private final DataSource primaryDataSource;
    private final DataSource dedicatedQueryDataSource;
    private final boolean dedicatedConfigured;
    private final Counter fallbackCounter;
    private final Clock clock;
    private volatile OffsetDateTime lastFallbackAt;
    private volatile String lastFallbackReason;

    public QueryRoutingDataSource(
            DataSource primaryDataSource,
            DataSource dedicatedQueryDataSource,
            boolean dedicatedConfigured,
            MeterRegistry meterRegistry) {
        this.primaryDataSource = primaryDataSource;
        this.dedicatedQueryDataSource = dedicatedQueryDataSource;
        this.dedicatedConfigured = dedicatedConfigured;
        this.fallbackCounter = Counter.builder("link.query.datasource.fallback")
                .description("Number of query datasource fallbacks to the primary datasource")
                .register(meterRegistry);
        this.clock = Clock.systemUTC();
        Gauge.builder("link.query.datasource.dedicated.configured", this, source -> source.dedicatedConfigured ? 1 : 0)
                .description("Whether a dedicated query datasource is configured")
                .register(meterRegistry);
        Gauge.builder("link.query.datasource.fallback.active", this, source -> source.lastFallbackAt == null ? 0 : 1)
                .description("Whether query datasource fallback has been used since startup")
                .register(meterRegistry);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnectionInternal(null, null);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnectionInternal(username, password);
    }

    public boolean isDedicatedConfigured() {
        return dedicatedConfigured;
    }

    public boolean isUsingPrimaryByDefault() {
        return dedicatedQueryDataSource == null;
    }

    public OffsetDateTime getLastFallbackAt() {
        return lastFallbackAt;
    }

    public String getLastFallbackReason() {
        return lastFallbackReason;
    }

    public boolean isDedicatedAvailable() {
        if (dedicatedQueryDataSource == null) {
            return false;
        }
        try (Connection ignored = dedicatedQueryDataSource.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public PrintWriter getLogWriter() {
        try {
            return primaryDataSource.getLogWriter();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read the primary datasource log writer", exception);
        }
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        primaryDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        primaryDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return primaryDataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        try {
            return primaryDataSource.getParentLogger();
        } catch (SQLFeatureNotSupportedException exception) {
            throw new IllegalStateException("Primary datasource does not expose a parent logger", exception);
        }
    }

    private Connection getConnectionInternal(String username, String password) throws SQLException {
        if (dedicatedQueryDataSource == null) {
            return primaryConnection(username, password);
        }
        try {
            return dedicatedConnection(username, password);
        } catch (SQLException exception) {
            fallbackCounter.increment();
            lastFallbackAt = OffsetDateTime.now(clock);
            lastFallbackReason = compactReason(exception);
            log.warn("link_query_datasource_fallback reason={}", lastFallbackReason);
            return primaryConnection(username, password);
        }
    }

    private Connection dedicatedConnection(String username, String password) throws SQLException {
        if (username == null) {
            return dedicatedQueryDataSource.getConnection();
        }
        return dedicatedQueryDataSource.getConnection(username, password);
    }

    private Connection primaryConnection(String username, String password) throws SQLException {
        if (username == null) {
            return primaryDataSource.getConnection();
        }
        return primaryDataSource.getConnection(username, password);
    }

    private String compactReason(SQLException exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 255 ? summary : summary.substring(0, 255);
    }
}
