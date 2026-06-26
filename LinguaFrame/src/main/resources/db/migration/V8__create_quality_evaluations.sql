CREATE TABLE quality_evaluations (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    language VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    verdict VARCHAR(64) NOT NULL,
    completeness INT NOT NULL,
    readability INT NOT NULL,
    timing_preservation INT NOT NULL,
    naturalness INT NOT NULL,
    issues_json TEXT NOT NULL,
    suggested_fixes_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    safe_error_summary VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_quality_evaluations_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_quality_evaluations_job_language_created
    ON quality_evaluations(job_id, language, created_at);
