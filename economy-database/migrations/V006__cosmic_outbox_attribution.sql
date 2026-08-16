ALTER TABLE cosmic_outbox_receipt
    ADD COLUMN run_id UUID REFERENCES simulation_run(run_id),
    ADD COLUMN logical_at TIMESTAMPTZ,
    ADD COLUMN decision_id TEXT,
    ADD COLUMN activity_id TEXT,
    ADD COLUMN config_revision TEXT,
    ADD COLUMN catalog_revision TEXT,
    ADD COLUMN reason_code TEXT,
    ADD COLUMN primary_is_agent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN secondary_is_agent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT cosmic_outbox_run_time_pair CHECK ((run_id IS NULL) = (logical_at IS NULL));

CREATE INDEX cosmic_outbox_run_logical_idx ON cosmic_outbox_receipt(run_id, logical_at);
