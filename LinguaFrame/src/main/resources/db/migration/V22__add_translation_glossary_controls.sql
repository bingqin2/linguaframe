ALTER TABLE localization_jobs
    ADD COLUMN translation_glossary_json VARCHAR(2048) NOT NULL DEFAULT '[]';

ALTER TABLE localization_jobs
    ADD COLUMN translation_glossary_hash VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE localization_jobs
    ADD COLUMN translation_glossary_entry_count INT NOT NULL DEFAULT 0;

ALTER TABLE translation_cache_entries
    ADD COLUMN translation_glossary_hash VARCHAR(64) NOT NULL DEFAULT '';
