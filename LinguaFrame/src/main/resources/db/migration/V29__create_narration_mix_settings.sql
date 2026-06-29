CREATE TABLE narration_mix_settings (
    job_id VARCHAR(64) PRIMARY KEY,
    ducking_volume DECIMAL(4, 3) NOT NULL,
    narration_volume DECIMAL(4, 3) NOT NULL,
    fade_duration_ms INT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
