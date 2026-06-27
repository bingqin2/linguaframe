ALTER TABLE model_call_records
    ADD COLUMN input_summary VARCHAR(512) NULL;

ALTER TABLE model_call_records
    ADD COLUMN output_summary VARCHAR(512) NULL;
