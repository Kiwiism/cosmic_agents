ALTER TABLE economic_transaction ADD COLUMN listing_id TEXT;
CREATE INDEX economic_transaction_listing_time_idx
    ON economic_transaction(run_id, listing_id, logical_at);

CREATE TABLE market_listing_lot (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    listing_id TEXT NOT NULL,
    lot_id TEXT NOT NULL,
    quantity_initial BIGINT NOT NULL CHECK (quantity_initial > 0),
    PRIMARY KEY (run_id, listing_id, lot_id),
    FOREIGN KEY (run_id, listing_id) REFERENCES market_listing(run_id, listing_id),
    FOREIGN KEY (run_id, lot_id) REFERENCES item_lot(run_id, lot_id)
);

CREATE TABLE agent_presence_event (
    presence_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    logical_at TIMESTAMPTZ NOT NULL,
    map_id INTEGER NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    visible BOOLEAN NOT NULL,
    reason TEXT NOT NULL
);
CREATE INDEX agent_presence_latest_idx
    ON agent_presence_event(run_id, agent_id, logical_at DESC);
