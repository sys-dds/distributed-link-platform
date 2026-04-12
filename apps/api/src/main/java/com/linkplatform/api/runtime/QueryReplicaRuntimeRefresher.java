package com.linkplatform.api.runtime;

import java.time.Clock;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class QueryReplicaRuntimeRefresher {

    private static final String PRIMARY_QUERY_REPLICA = "primary-query-replica";

    private final ObjectProvider<DataSource> queryDataSourceProvider;
    private final QueryReplicaRuntimeStore queryReplicaRuntimeStore;
    private final Clock clock;

    public QueryReplicaRuntimeRefresher(
            @Qualifier("queryDataSource") ObjectProvider<DataSource> queryDataSourceProvider,
            QueryReplicaRuntimeStore queryReplicaRuntimeStore,
            Clock clock) {
        this.queryDataSourceProvider = queryDataSourceProvider;
        this.queryReplicaRuntimeStore = queryReplicaRuntimeStore;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${link-platform.query-replica.refresh-delay-ms:10000}")
    public void refreshReplicaRuntimeState() {
        DataSource queryDataSource = queryDataSourceProvider.getIfAvailable();
        if (!(queryDataSource instanceof QueryRoutingDataSource queryRoutingDataSource)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        QueryReplicaProbeResult probeResult = queryRoutingDataSource.probeDedicatedQueryDataSource();
        queryReplicaRuntimeStore.recordProbe(
                PRIMARY_QUERY_REPLICA,
                queryRoutingDataSource.isQueryReplicaEnabled(),
                probeResult,
                now);
    }
}
