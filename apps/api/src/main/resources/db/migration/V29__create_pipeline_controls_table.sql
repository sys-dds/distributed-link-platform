CREATE TABLE pipeline_controls (
    pipeline_name VARCHAR(50) PRIMARY KEY,
    paused BOOLEAN NOT NULL,
    pause_reason VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_force_tick_at TIMESTAMP WITH TIME ZONE,
    last_requeue_at TIMESTAMP WITH TIME ZONE,
    last_relay_success_at TIMESTAMP WITH TIME ZONE,
    last_relay_failure_at TIMESTAMP WITH TIME ZONE,
    last_relay_failure_reason VARCHAR(512)
);

INSERT INTO pipeline_controls (pipeline_name, paused, pause_reason, updated_at)
VALUES
    ('analytics', FALSE, NULL, CURRENT_TIMESTAMP),
    ('lifecycle', FALSE, NULL, CURRENT_TIMESTAMP);
