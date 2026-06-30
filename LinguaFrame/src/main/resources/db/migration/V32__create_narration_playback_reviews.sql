CREATE TABLE narration_playback_reviews (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    segment_index INT NOT NULL,
    decision VARCHAR(32) NOT NULL,
    issue_categories VARCHAR(255) NOT NULL,
    reviewer_note TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_narration_playback_reviews_job_segment (job_id, segment_index),
    INDEX idx_narration_playback_reviews_job_segment (job_id, segment_index),
    CONSTRAINT fk_narration_playback_reviews_job
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE
);
