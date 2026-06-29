ALTER TABLE subtitle_draft_segments
    ADD COLUMN review_decision VARCHAR(32) NOT NULL DEFAULT 'EDITED';

ALTER TABLE subtitle_draft_segments
    ADD COLUMN issue_categories VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE subtitle_draft_segments
    ADD COLUMN reviewer_note TEXT NULL;
