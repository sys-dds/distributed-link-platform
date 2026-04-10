CREATE TABLE governance_daily_rollups (
    bucket_day DATE NOT NULL,
    workspaces_total BIGINT NOT NULL,
    suspended_workspaces_total BIGINT NOT NULL,
    members_total BIGINT NOT NULL,
    service_accounts_total BIGINT NOT NULL,
    open_abuse_cases_total BIGINT NOT NULL,
    quarantined_links_total BIGINT NOT NULL,
    failing_webhook_subscriptions_total BIGINT NOT NULL,
    over_quota_workspaces_total BIGINT NOT NULL,
    PRIMARY KEY (bucket_day)
);
