CREATE TABLE model_call_records (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    audio_seconds DECIMAL(12,3) NULL,
    character_count INT NULL,
    estimated_cost_usd DECIMAL(18,8) NOT NULL,
    safe_error_summary VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_model_call_records_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_model_call_records_job_created
    ON model_call_records(job_id, created_at);
