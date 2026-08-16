ALTER TABLE cosmic_outbox_receipt
    ADD COLUMN payload JSONB NOT NULL DEFAULT '{}';

CREATE INDEX cosmic_outbox_payload_gin_idx ON cosmic_outbox_receipt USING GIN(payload);
