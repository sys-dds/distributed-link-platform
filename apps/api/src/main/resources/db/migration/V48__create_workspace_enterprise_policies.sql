CREATE TABLE workspace_enterprise_policies (
    workspace_id BIGINT PRIMARY KEY,
    require_api_key_expiry BOOLEAN NOT NULL,
    max_api_key_ttl_days INTEGER NULL,
    require_service_account_key_expiry BOOLEAN NOT NULL,
    max_service_account_key_ttl_days INTEGER NULL,
    require_dual_control_for_ops BOOLEAN NOT NULL,
    require_dual_control_for_plan_changes BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_owner_id BIGINT NOT NULL
);

INSERT INTO workspace_enterprise_policies (
    workspace_id,
    require_api_key_expiry,
    max_api_key_ttl_days,
    require_service_account_key_expiry,
    max_service_account_key_ttl_days,
    require_dual_control_for_ops,
    require_dual_control_for_plan_changes,
    updated_at,
    updated_by_owner_id
)
SELECT
    id,
    FALSE,
    NULL,
    FALSE,
    NULL,
    FALSE,
    FALSE,
    now(),
    COALESCE(created_by_owner_id, 0)
FROM workspaces;
