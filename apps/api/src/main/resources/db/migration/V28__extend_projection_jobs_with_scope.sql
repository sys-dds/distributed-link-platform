ALTER TABLE projection_jobs
    ADD COLUMN owner_id BIGINT REFERENCES owners (id);

ALTER TABLE projection_jobs
    ADD COLUMN slug VARCHAR(100);

CREATE INDEX idx_projection_jobs_scope_owner
    ON projection_jobs (owner_id, requested_at DESC, id DESC);

CREATE INDEX idx_projection_jobs_scope_slug
    ON projection_jobs (slug, requested_at DESC, id DESC);
