CREATE TABLE job_dispatch_events (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error VARCHAR(512),
    dispatched_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_dispatch_events_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
);

CREATE INDEX idx_job_dispatch_events_ready
    ON job_dispatch_events(status, next_attempt_at, created_at);

CREATE INDEX idx_job_dispatch_events_job_id
    ON job_dispatch_events(job_id);
