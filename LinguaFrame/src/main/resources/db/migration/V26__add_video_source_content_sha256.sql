ALTER TABLE videos
    ADD COLUMN source_content_sha256 VARCHAR(64);

CREATE INDEX idx_videos_owner_source_sha_created_at
    ON videos (owner_id, source_content_sha256, created_at);
