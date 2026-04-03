ALTER TABLE link_clicks
ADD COLUMN event_id VARCHAR(64);

CREATE UNIQUE INDEX idx_link_clicks_event_id
    ON link_clicks (event_id);
