CREATE TABLE narration_mix_keyframes (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    lane VARCHAR(32) NOT NULL,
    time_seconds DECIMAL(10, 3) NOT NULL,
    mix_value DECIMAL(8, 3) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_narration_mix_keyframes_job_lane_time (job_id, lane, time_seconds),
    INDEX idx_narration_mix_keyframes_job_lane_time (job_id, lane, time_seconds),
    CONSTRAINT fk_narration_mix_keyframes_job
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE
);
