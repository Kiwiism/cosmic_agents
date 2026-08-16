CREATE TABLE agent_character_binding (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    character_id INTEGER NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, agent_id),
    UNIQUE (run_id, character_id)
);

ALTER TABLE item_lot ADD COLUMN fingerprint CHAR(64);
CREATE INDEX item_lot_fifo_idx ON item_lot(run_id, item_id, fingerprint, created_event_id);

CREATE TABLE economic_ingestion_failure (
    outbox_id UUID PRIMARY KEY REFERENCES cosmic_outbox_receipt(outbox_id),
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    failed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 1 CHECK (attempts > 0),
    error_class TEXT NOT NULL,
    error_message TEXT NOT NULL,
    receipt JSONB NOT NULL
);
CREATE INDEX economic_ingestion_failure_run_idx ON economic_ingestion_failure(run_id, failed_at);
