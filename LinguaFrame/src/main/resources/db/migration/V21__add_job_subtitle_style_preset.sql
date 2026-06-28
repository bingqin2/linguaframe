ALTER TABLE localization_jobs
    ADD COLUMN subtitle_style_preset VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
