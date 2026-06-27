ALTER TABLE model_call_records
    ADD COLUMN budget_identity VARCHAR(128) NOT NULL DEFAULT 'demo-owner';

CREATE INDEX idx_model_call_records_budget_identity_created
    ON model_call_records(budget_identity, created_at);
