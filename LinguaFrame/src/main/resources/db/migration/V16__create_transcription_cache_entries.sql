CREATE TABLE transcription_cache_entries (
    id VARCHAR(36) PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL,
    audio_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    source_job_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_transcription_cache_entries_cache_key
        UNIQUE (cache_key)
);

CREATE INDEX idx_transcription_cache_entries_audio_hash
    ON transcription_cache_entries(audio_hash, provider, model, prompt_version, created_at);
