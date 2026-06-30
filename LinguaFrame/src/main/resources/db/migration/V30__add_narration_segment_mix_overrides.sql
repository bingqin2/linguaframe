ALTER TABLE narration_segments ADD COLUMN ducking_volume DECIMAL(4, 3) NULL;
ALTER TABLE narration_segments ADD COLUMN narration_volume DECIMAL(4, 3) NULL;
ALTER TABLE narration_segments ADD COLUMN fade_duration_ms INT NULL;
