ALTER TABLE analytics_outbox
    ADD COLUMN claimed_by VARCHAR(64);

ALTER TABLE analytics_outbox
    ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_analytics_outbox_claimable
    ON analytics_outbox (published_at, claimed_until, created_at, id);
