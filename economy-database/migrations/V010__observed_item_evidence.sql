ALTER TABLE market_observation
    ADD COLUMN item_fingerprint TEXT NOT NULL DEFAULT '',
    ADD COLUMN item_attributes JSONB NOT NULL DEFAULT '{}';
