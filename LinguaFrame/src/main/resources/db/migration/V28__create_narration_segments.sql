CREATE TABLE narration_segments (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    segment_index INT NOT NULL,
    start_seconds DECIMAL(10, 3) NOT NULL,
    end_seconds DECIMAL(10, 3) NOT NULL,
    text TEXT NOT NULL,
    voice VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_narration_segments_job_index (job_id, segment_index),
    INDEX idx_narration_segments_job (job_id)
);
