CREATE TABLE transcript_segments (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    segment_index INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transcript_segments_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_transcript_segments_job_index
        UNIQUE (job_id, segment_index)
);

CREATE INDEX idx_transcript_segments_job_index
    ON transcript_segments(job_id, segment_index);
