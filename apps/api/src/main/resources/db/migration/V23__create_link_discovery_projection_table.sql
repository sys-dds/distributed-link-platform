CREATE TABLE link_discovery_projection (
    slug VARCHAR(100) PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES owners (id),
    original_url TEXT NOT NULL,
    title VARCHAR(255),
    hostname VARCHAR(255),
    tags_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    lifecycle_state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_link_discovery_projection_owner_updated
    ON link_discovery_projection (owner_id, updated_at DESC, slug ASC);

CREATE INDEX idx_link_discovery_projection_owner_created
    ON link_discovery_projection (owner_id, created_at DESC, slug ASC);

CREATE INDEX idx_link_discovery_projection_owner_hostname
    ON link_discovery_projection (owner_id, hostname, slug);

CREATE INDEX idx_link_discovery_projection_owner_lifecycle
    ON link_discovery_projection (owner_id, lifecycle_state, slug);
