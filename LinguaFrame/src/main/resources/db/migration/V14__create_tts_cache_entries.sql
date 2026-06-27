CREATE TABLE tts_cache_entries (
    id VARCHAR(36) PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL,
    text_hash VARCHAR(64) NOT NULL,
    language VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    voice VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    source_job_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_tts_cache_entries_cache_key
        UNIQUE (cache_key)
);

CREATE INDEX idx_tts_cache_entries_compatibility
    ON tts_cache_entries(language, provider, model, voice, created_at);
