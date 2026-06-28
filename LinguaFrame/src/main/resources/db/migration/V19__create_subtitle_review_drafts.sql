CREATE TABLE subtitle_draft_segments (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    language VARCHAR(32) NOT NULL,
    segment_index INT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_subtitle_draft_job_language_index UNIQUE (job_id, language, segment_index),
    INDEX idx_subtitle_draft_job_language (job_id, language)
);
