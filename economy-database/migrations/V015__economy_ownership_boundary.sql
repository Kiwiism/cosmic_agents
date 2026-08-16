CREATE TABLE inventory_review (
    review_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    character_id INTEGER NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('FM_ENTRY_SCAN','FM_MARKET_APPRAISAL')),
    inventory_revision CHAR(64) NOT NULL,
    snapshot JSONB NOT NULL
);
CREATE INDEX inventory_review_agent_time_idx ON inventory_review(run_id, agent_id, logical_time);

CREATE TABLE item_disposition_decision (
    decision_id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES inventory_review(review_id) ON DELETE CASCADE,
    inventory_type TEXT NOT NULL,
    inventory_slot SMALLINT NOT NULL,
    item_id INTEGER NOT NULL,
    item_fingerprint CHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    disposition TEXT NOT NULL CHECK (disposition IN
        ('PROTECTED_UNREVIEWED','KEEP_REVIEWED','NPC_SALE_AUTHORIZED','PLAYER_SHOP_LISTING_RESERVED')),
    reason TEXT NOT NULL,
    legacy_action TEXT NOT NULL,
    shadow_action TEXT NOT NULL,
    shadow_disagreement BOOLEAN NOT NULL
);
CREATE INDEX item_disposition_review_idx ON item_disposition_decision(review_id, item_id);
CREATE INDEX item_disposition_disagreement_idx ON item_disposition_decision(review_id)
    WHERE shadow_disagreement;

CREATE TABLE economic_asset_reservation (
    reservation_id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES inventory_review(review_id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    inventory_type TEXT NOT NULL,
    inventory_slot SMALLINT NOT NULL,
    item_id INTEGER NOT NULL,
    item_fingerprint CHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    action TEXT NOT NULL,
    venue TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','REVOKED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX economic_asset_reservation_active_idx
    ON economic_asset_reservation(run_id, agent_id, item_id) WHERE status='ACTIVE';

CREATE TABLE economic_action_authorization (
    authorization_id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES inventory_review(review_id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    inventory_type TEXT NOT NULL,
    inventory_slot SMALLINT NOT NULL,
    item_id INTEGER NOT NULL,
    item_fingerprint CHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    action TEXT NOT NULL,
    venue TEXT NOT NULL,
    inventory_revision CHAR(64) NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','CONSUMED','REVOKED','EXPIRED')),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX economic_action_authorization_active_idx
    ON economic_action_authorization(run_id, agent_id, action) WHERE status='ACTIVE';

CREATE TABLE economic_action_guard_event (
    guard_event_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    character_id INTEGER NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    action TEXT NOT NULL,
    inventory_type TEXT NOT NULL,
    inventory_slot SMALLINT NOT NULL,
    item_id INTEGER NOT NULL,
    item_fingerprint CHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    allowed BOOLEAN NOT NULL,
    reason TEXT NOT NULL,
    authorization_id UUID REFERENCES economic_action_authorization(authorization_id)
);
CREATE INDEX economic_action_guard_event_agent_time_idx
    ON economic_action_guard_event(run_id, agent_id, logical_time);
