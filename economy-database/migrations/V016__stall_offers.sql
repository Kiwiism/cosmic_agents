CREATE TABLE stall_offer (
    offer_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    buyer_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    stall_id TEXT NOT NULL,
    listing_id TEXT NOT NULL,
    room_map_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    item_fingerprint TEXT NOT NULL,
    item_attributes JSONB NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    ask_mesos BIGINT NOT NULL CHECK (ask_mesos > 0),
    offered_mesos BIGINT NOT NULL CHECK (offered_mesos > 0),
    public_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED_AWAITING_SETTLEMENT','REJECTED',
        'EXPIRED','CANCELLED_LISTING_CHANGED','EXECUTED','FAILED')),
    response_text TEXT,
    responded_at TIMESTAMPTZ,
    settlement_transaction_id TEXT,
    CHECK (expires_at > created_at),
    CHECK (buyer_id <> seller_id)
);

CREATE INDEX stall_offer_seller_pending_idx
    ON stall_offer(run_id, seller_id, status, created_at);
CREATE INDEX stall_offer_item_history_idx
    ON stall_offer(run_id, item_id, created_at);
