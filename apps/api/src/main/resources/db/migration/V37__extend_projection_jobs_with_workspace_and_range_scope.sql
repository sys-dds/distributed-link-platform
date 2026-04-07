ALTER TABLE projection_jobs
    ADD COLUMN range_start TIMESTAMP WITH TIME ZONE;

ALTER TABLE projection_jobs
    ADD COLUMN range_end TIMESTAMP WITH TIME ZONE;

ALTER TABLE projection_jobs
    ADD COLUMN requested_by_owner_id BIGINT REFERENCES owners (id);

ALTER TABLE projection_jobs
    ADD COLUMN operator_note VARCHAR(512);

CREATE INDEX idx_projection_jobs_workspace_type_status
    ON projection_jobs (workspace_id, job_type, status);

CREATE INDEX idx_projection_jobs_slug_range
    ON projection_jobs (slug, range_start, range_end);
