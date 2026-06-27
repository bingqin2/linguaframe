CREATE TABLE translation_cache_entries (
    id VARCHAR(36) PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    target_language VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    source_job_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_translation_cache_entries_cache_key
        UNIQUE (cache_key)
);

CREATE INDEX idx_translation_cache_entries_compatibility
    ON translation_cache_entries(target_language, provider, model, prompt_version, created_at);
