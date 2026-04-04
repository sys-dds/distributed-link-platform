ALTER TABLE link_activity_events
    ADD COLUMN event_id VARCHAR(100);

UPDATE link_activity_events
SET event_id = 'legacy-' || id
WHERE event_id IS NULL;

ALTER TABLE link_activity_events
    ALTER COLUMN event_id SET NOT NULL;

CREATE UNIQUE INDEX uq_link_activity_events_event_id
    ON link_activity_events (event_id);
