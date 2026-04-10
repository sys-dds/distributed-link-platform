package com.linkplatform.api.runtime;

import com.linkplatform.api.owner.application.SecurityEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPlatformQueryProperties.class)
public class QueryReadConfiguration {

    @Bean
    QueryRoutingDataSourceHolder queryRoutingDataSourceHolder(
            @Qualifier("dataSource") DataSource dataSource,
            LinkPlatformQueryProperties queryProperties,
            LinkPlatformRuntimeProperties runtimeProperties,
            QueryReplicaRuntimeStore queryReplicaRuntimeStore,
            MeterRegistry meterRegistry,
            SecurityEventStore securityEventStore,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${link-platform.query-replica.enabled:false}") boolean queryReplicaEnabled,
            @org.springframework.beans.factory.annotation.Value("${link-platform.query-replica.max-lag-seconds:30}") long maxLagSeconds,
            @org.springframework.beans.factory.annotation.Value("${link-platform.query-replica.fallback-log-enabled:true}") boolean fallbackLogEnabled) {
        runtimeProperties.getMode();
        return new QueryRoutingDataSourceHolder(new QueryRoutingDataSource(
                dataSource,
                dedicatedQueryDataSource(queryProperties),
                queryProperties.isDedicatedConfigured(),
                queryReplicaEnabled,
                fallbackLogEnabled,
                queryReplicaRuntimeStore,
                new QueryReplicaLagPolicy(maxLagSeconds, clock),
                meterRegistry,
                securityEventStore,
                clock));
    }

    @Bean(name = "queryDataSourceHealthIndicator")
    QueryDataSourceHealthIndicator queryDataSourceHealthIndicator(
            QueryRoutingDataSourceHolder queryRoutingDataSourceHolder,
            LinkPlatformRuntimeProperties runtimeProperties) {
        return new QueryDataSourceHealthIndicator(queryRoutingDataSourceHolder.dataSource(), runtimeProperties);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("queryJdbcTemplate")
    JdbcTemplate queryJdbcTemplate(QueryRoutingDataSourceHolder queryRoutingDataSourceHolder) {
        return new JdbcTemplate(queryRoutingDataSourceHolder.dataSource());
    }

    private DataSource dedicatedQueryDataSource(LinkPlatformQueryProperties queryProperties) {
        if (!queryProperties.isDedicatedConfigured()) {
            return null;
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(queryProperties.getUrl());
        if (queryProperties.getDriverClassName() != null) {
            dataSource.setDriverClassName(queryProperties.getDriverClassName());
        }
        dataSource.setUsername(queryProperties.getUsername());
        dataSource.setPassword(queryProperties.getPassword());
        return dataSource;
    }
}
