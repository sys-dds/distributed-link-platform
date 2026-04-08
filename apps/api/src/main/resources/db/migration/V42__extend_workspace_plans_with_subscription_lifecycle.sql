ALTER TABLE workspace_plans
    ADD COLUMN subscription_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN current_period_end TIMESTAMPTZ NOT NULL DEFAULT now() + interval '30 days',
    ADD COLUMN grace_until TIMESTAMPTZ NULL,
    ADD COLUMN scheduled_plan_code VARCHAR(32) NULL,
    ADD COLUMN scheduled_plan_effective_at TIMESTAMPTZ NULL;
