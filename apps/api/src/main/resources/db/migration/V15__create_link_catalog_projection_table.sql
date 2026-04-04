CREATE TABLE link_catalog_projection (
    slug VARCHAR(100) PRIMARY KEY,
    original_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title VARCHAR(255),
    tags_json TEXT,
    hostname VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_link_catalog_projection_created_at
    ON link_catalog_projection (created_at DESC, slug ASC);

CREATE INDEX idx_link_catalog_projection_deleted_at
    ON link_catalog_projection (deleted_at);
