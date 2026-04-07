ALTER TABLE links
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE owner_api_keys
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE owner_api_keys
    ADD COLUMN scopes_json JSONB NOT NULL DEFAULT '["links:read","links:write","analytics:read","api_keys:read","api_keys:write","members:read","members:write","ops:read","ops:write"]';

ALTER TABLE owner_security_events
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE link_catalog_projection
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE link_discovery_projection
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE link_activity_events
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

ALTER TABLE projection_jobs
    ADD COLUMN workspace_id BIGINT REFERENCES workspaces (id);

CREATE INDEX idx_links_workspace_id
    ON links (workspace_id);

CREATE INDEX idx_owner_api_keys_workspace_id
    ON owner_api_keys (workspace_id);

CREATE INDEX idx_owner_security_events_workspace_occurred_at
    ON owner_security_events (workspace_id, occurred_at DESC);

CREATE INDEX idx_link_catalog_projection_workspace_updated
    ON link_catalog_projection (workspace_id, updated_at DESC, slug ASC);

CREATE INDEX idx_link_discovery_projection_workspace_updated
    ON link_discovery_projection (workspace_id, updated_at DESC, slug ASC);

CREATE INDEX idx_link_activity_events_workspace_occurred
    ON link_activity_events (workspace_id, occurred_at DESC, id DESC);

CREATE INDEX idx_projection_jobs_workspace_requested
    ON projection_jobs (workspace_id, requested_at DESC, id DESC);
