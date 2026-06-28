ALTER TABLE localization_jobs
    ADD COLUMN subtitle_polishing_mode VARCHAR(32) NOT NULL DEFAULT 'OFF';

CREATE TABLE subtitle_polishing_cache_entries (
    id VARCHAR(36) PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL UNIQUE,
    source_hash VARCHAR(64) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    subtitle_polishing_mode VARCHAR(32) NOT NULL,
    response_json CLOB NOT NULL,
    source_job_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_subtitle_polishing_cache_created_at
    ON subtitle_polishing_cache_entries(created_at);
