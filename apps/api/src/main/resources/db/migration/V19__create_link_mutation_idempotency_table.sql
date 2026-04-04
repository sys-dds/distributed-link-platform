CREATE TABLE link_mutation_idempotency (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    operation VARCHAR(32) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
