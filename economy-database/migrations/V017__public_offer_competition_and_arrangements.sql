ALTER TABLE stall_offer DROP CONSTRAINT stall_offer_status_check;
ALTER TABLE stall_offer ADD CONSTRAINT stall_offer_status_check CHECK (status IN (
    'PENDING', 'ACCEPTED_AWAITING_SETTLEMENT', 'OUTBID', 'REJECTED', 'EXPIRED',
    'CANCELLED_LISTING_CHANGED', 'EXECUTED', 'FAILED'
));

CREATE INDEX stall_offer_listing_bid_idx
    ON stall_offer(run_id, listing_id, status, offered_mesos DESC, created_at);

CREATE TABLE private_trade_arrangement (
    arrangement_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    offer_id UUID NOT NULL UNIQUE REFERENCES stall_offer(offer_id),
    buyer_id VARCHAR(96) NOT NULL,
    seller_id VARCHAR(96) NOT NULL,
    stall_id VARCHAR(160) NOT NULL,
    listing_id VARCHAR(192) NOT NULL,
    room_map_id INTEGER NOT NULL CHECK (room_map_id BETWEEN 910000001 AND 910000022),
    item_id INTEGER NOT NULL CHECK (item_id > 0),
    item_fingerprint VARCHAR(256) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    agreed_mesos BIGINT NOT NULL CHECK (agreed_mesos > 0),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(48) NOT NULL CHECK (status IN (
        'PENDING_MEETUP', 'EXECUTED', 'EXPIRED', 'CANCELLED_LISTING_CHANGED',
        'CANCELLED_PARTICIPANT'
    )),
    settlement_transaction_id VARCHAR(160),
    settled_at TIMESTAMPTZ,
    CHECK (buyer_id <> seller_id),
    CHECK (expires_at > created_at)
);

CREATE INDEX private_trade_arrangement_buyer_idx
    ON private_trade_arrangement(run_id, buyer_id, status, created_at);
CREATE INDEX private_trade_arrangement_seller_idx
    ON private_trade_arrangement(run_id, seller_id, status, created_at);
CREATE INDEX private_trade_arrangement_listing_idx
    ON private_trade_arrangement(run_id, listing_id, created_at);
