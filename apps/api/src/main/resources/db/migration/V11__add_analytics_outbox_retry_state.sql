ALTER TABLE analytics_outbox
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

ALTER TABLE analytics_outbox
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE analytics_outbox
    ADD COLUMN last_error_summary VARCHAR(255);

ALTER TABLE analytics_outbox
    ADD COLUMN parked_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_analytics_outbox_delivery
    ON analytics_outbox (published_at, parked_at, next_attempt_at, claimed_until, created_at, id);
