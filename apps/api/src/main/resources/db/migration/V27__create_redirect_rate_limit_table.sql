CREATE TABLE redirect_rate_limits (
    subject_hash CHAR(64) NOT NULL,
    slug VARCHAR(255),
    bucket_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    window_seconds INT NOT NULL,
    request_count INT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (subject_hash, slug, bucket_started_at, window_seconds)
);

CREATE INDEX idx_redirect_rate_limits_expires_at
    ON redirect_rate_limits (expires_at);

ALTER TABLE links
    ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE link_catalog_projection
    ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE link_discovery_projection
    ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
