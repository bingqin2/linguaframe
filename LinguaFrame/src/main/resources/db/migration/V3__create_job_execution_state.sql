ALTER TABLE localization_jobs
    ADD COLUMN started_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN completed_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failed_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failure_stage VARCHAR(64) NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failure_reason VARCHAR(512) NULL;

ALTER TABLE localization_jobs
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE localization_jobs
    ADD COLUMN updated_at TIMESTAMP NULL;

UPDATE localization_jobs
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE localization_jobs
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL;

CREATE TABLE job_timeline_events (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NOT NULL,
    duration_ms BIGINT NULL,
    error_summary VARCHAR(512) NULL,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_timeline_events_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
);

CREATE INDEX idx_job_timeline_events_job_id_occurred
    ON job_timeline_events(job_id, occurred_at);
