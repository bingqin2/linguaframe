ALTER TABLE videos
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT 'demo-owner';

ALTER TABLE localization_jobs
    ADD COLUMN owner_id VARCHAR(128) NOT NULL DEFAULT 'demo-owner';

CREATE INDEX idx_videos_owner_created_at ON videos(owner_id, created_at);

CREATE INDEX idx_localization_jobs_owner_created_at ON localization_jobs(owner_id, created_at);

CREATE INDEX idx_localization_jobs_owner_video_id ON localization_jobs(owner_id, video_id);
