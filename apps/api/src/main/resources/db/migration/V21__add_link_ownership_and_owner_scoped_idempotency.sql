ALTER TABLE links ADD COLUMN owner_id BIGINT DEFAULT 100;

UPDATE links
SET owner_id = 100
WHERE owner_id IS NULL;

ALTER TABLE links ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE links ADD CONSTRAINT fk_links_owner
    FOREIGN KEY (owner_id) REFERENCES owners (id);

CREATE INDEX idx_links_owner_id ON links (owner_id);

CREATE TABLE link_mutation_idempotency_v2 (
    owner_id BIGINT NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, idempotency_key),
    CONSTRAINT fk_link_mutation_idempotency_owner
        FOREIGN KEY (owner_id) REFERENCES owners (id)
);

INSERT INTO link_mutation_idempotency_v2 (owner_id, idempotency_key, operation, request_hash, response_json, created_at)
SELECT 100, idempotency_key, operation, request_hash, response_json, created_at
FROM link_mutation_idempotency;

DROP TABLE link_mutation_idempotency;

ALTER TABLE link_mutation_idempotency_v2 RENAME TO link_mutation_idempotency;
