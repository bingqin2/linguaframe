ALTER TABLE job_artifacts
    ADD COLUMN cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE job_artifacts
    ADD COLUMN source_artifact_id VARCHAR(36);

CREATE INDEX idx_job_artifacts_reuse_lookup
    ON job_artifacts(type, cache_hit, created_at);
