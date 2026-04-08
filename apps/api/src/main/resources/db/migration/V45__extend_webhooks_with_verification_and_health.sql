ALTER TABLE webhook_subscriptions
    ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN verified_at TIMESTAMPTZ NULL,
    ADD COLUMN last_test_fired_at TIMESTAMPTZ NULL,
    ADD COLUMN last_test_delivery_id BIGINT NULL;
