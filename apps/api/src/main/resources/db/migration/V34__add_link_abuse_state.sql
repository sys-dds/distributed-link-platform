ALTER TABLE links
    ADD COLUMN abuse_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE links
    ADD COLUMN abuse_reason VARCHAR(255);

ALTER TABLE links
    ADD COLUMN abuse_flagged_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE links
    ADD COLUMN abuse_reviewed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE links
    ADD COLUMN abuse_reviewed_by_owner_id BIGINT;

ALTER TABLE links
    ADD COLUMN abuse_review_note VARCHAR(512);

CREATE INDEX idx_links_workspace_abuse_status
    ON links (workspace_id, abuse_status);

CREATE INDEX idx_links_workspace_abuse_flagged_at
    ON links (workspace_id, abuse_flagged_at DESC);
