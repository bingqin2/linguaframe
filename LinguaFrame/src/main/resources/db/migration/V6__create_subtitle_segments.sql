CREATE TABLE subtitle_segments (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    language VARCHAR(32) NOT NULL,
    segment_index INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_subtitle_segments_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_subtitle_segments_job_language_index
        UNIQUE (job_id, language, segment_index)
);

CREATE INDEX idx_subtitle_segments_job_language_index
    ON subtitle_segments(job_id, language, segment_index);
