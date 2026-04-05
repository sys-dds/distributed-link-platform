ALTER TABLE link_activity_events
    ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1 REFERENCES owners (id);

CREATE INDEX idx_link_activity_events_owner_id_occurred_at
    ON link_activity_events (owner_id, occurred_at DESC, id DESC);
