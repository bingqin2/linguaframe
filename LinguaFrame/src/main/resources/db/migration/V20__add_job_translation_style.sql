ALTER TABLE localization_jobs
    ADD COLUMN translation_style VARCHAR(32) NOT NULL DEFAULT 'NATURAL';
