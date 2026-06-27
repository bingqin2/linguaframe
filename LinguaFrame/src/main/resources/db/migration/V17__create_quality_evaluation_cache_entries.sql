CREATE TABLE quality_evaluation_cache_entries (
    id VARCHAR(36) PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    target_hash VARCHAR(64) NOT NULL,
    language VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    source_job_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_quality_evaluation_cache_entries_cache_key
        UNIQUE (cache_key)
);

CREATE INDEX idx_quality_evaluation_cache_entries_lookup
    ON quality_evaluation_cache_entries(source_hash, target_hash, language, provider, model, prompt_version, created_at);
