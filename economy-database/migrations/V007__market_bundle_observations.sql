ALTER TABLE market_observation
    ADD COLUMN quantity_per_bundle INTEGER NOT NULL DEFAULT 1 CHECK (quantity_per_bundle > 0),
    ADD COLUMN bundles INTEGER NOT NULL DEFAULT 1 CHECK (bundles > 0),
    ADD COLUMN bundle_price BIGINT NOT NULL DEFAULT 1 CHECK (bundle_price > 0);
