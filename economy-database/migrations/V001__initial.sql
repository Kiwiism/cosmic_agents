CREATE TABLE simulation_run (
    run_id UUID PRIMARY KEY,
    scenario_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('CREATED','RUNNING','COMPLETED','FAILED','STOPPED')),
    logical_started_at TIMESTAMPTZ NOT NULL,
    logical_current_at TIMESTAMPTZ NOT NULL,
    target_logical_at TIMESTAMPTZ NOT NULL,
    seed BIGINT NOT NULL,
    config_hash CHAR(64) NOT NULL,
    config_yaml TEXT NOT NULL,
    catalog_version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    failure_reason TEXT
);

CREATE TABLE economic_event (
    event_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_time TIMESTAMPTZ NOT NULL,
    event_kind TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    causation_id TEXT,
    correlation_id TEXT,
    config_hash CHAR(64) NOT NULL,
    catalog_version TEXT NOT NULL,
    actor_ids JSONB NOT NULL DEFAULT '[]',
    evidence JSONB NOT NULL DEFAULT '{}',
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, idempotency_key)
);
CREATE INDEX economic_event_run_time_idx ON economic_event(run_id, logical_time, event_id);
CREATE INDEX economic_event_kind_time_idx ON economic_event(run_id, event_kind, logical_time);
CREATE INDEX economic_event_evidence_gin_idx ON economic_event USING GIN(evidence);

CREATE TABLE ledger_posting (
    event_id UUID NOT NULL REFERENCES economic_event(event_id) ON DELETE CASCADE,
    posting_index SMALLINT NOT NULL,
    account_type TEXT NOT NULL,
    account_owner_id TEXT NOT NULL,
    asset_type TEXT NOT NULL,
    asset_identifier TEXT NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity <> 0),
    lot_id TEXT,
    PRIMARY KEY (event_id, posting_index)
);
CREATE INDEX ledger_posting_asset_idx ON ledger_posting(asset_type, asset_identifier, event_id);
CREATE INDEX ledger_posting_account_idx ON ledger_posting(account_type, account_owner_id, event_id);
CREATE INDEX ledger_posting_lot_idx ON ledger_posting(lot_id) WHERE lot_id IS NOT NULL;

CREATE TABLE cosmic_outbox_receipt (
    outbox_id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    operation_kind TEXT NOT NULL,
    primary_character_id INTEGER NOT NULL,
    secondary_character_id INTEGER,
    summary TEXT NOT NULL,
    cosmic_created_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE decision_journal (
    decision_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    decision_kind TEXT NOT NULL,
    chosen_action JSONB NOT NULL,
    alternatives JSONB NOT NULL DEFAULT '[]',
    beliefs_used JSONB NOT NULL DEFAULT '{}',
    needs_used JSONB NOT NULL DEFAULT '{}',
    utility_breakdown JSONB NOT NULL DEFAULT '{}',
    random_stream TEXT,
    random_draw DOUBLE PRECISION,
    config_hash CHAR(64) NOT NULL,
    catalog_version TEXT NOT NULL
);
CREATE INDEX decision_journal_agent_time_idx ON decision_journal(run_id, agent_id, logical_time);

CREATE TABLE market_observation (
    observation_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    room_map_id INTEGER NOT NULL,
    stall_owner_id TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    listing_id TEXT NOT NULL,
    observed_state TEXT NOT NULL CHECK (observed_state IN ('LISTED','MISSING','SOLD_TO_OBSERVER'))
);
CREATE INDEX market_observation_item_time_idx ON market_observation(run_id, item_id, logical_time);
CREATE INDEX market_observation_agent_time_idx ON market_observation(run_id, agent_id, logical_time);

CREATE TABLE social_event (
    social_event_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_time TIMESTAMPTZ NOT NULL,
    room_map_id INTEGER NOT NULL,
    speaker_agent_id TEXT NOT NULL,
    target_agent_id TEXT,
    event_kind TEXT NOT NULL,
    public_text TEXT NOT NULL,
    structured_intent JSONB NOT NULL DEFAULT '{}',
    related_item_id INTEGER,
    related_event_id UUID REFERENCES economic_event(event_id)
);
CREATE INDEX social_event_room_time_idx ON social_event(run_id, room_map_id, logical_time);

CREATE TABLE simulation_checkpoint (
    checkpoint_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_time TIMESTAMPTZ NOT NULL,
    sequence BIGINT NOT NULL,
    config_hash CHAR(64) NOT NULL,
    catalog_version TEXT NOT NULL,
    state JSONB NOT NULL,
    state_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, sequence)
);

-- Rebuildable dashboard read models. Never use these tables to make agent decisions.
CREATE TABLE item_market_daily (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_date DATE NOT NULL,
    item_id INTEGER NOT NULL,
    completed_quantity BIGINT NOT NULL DEFAULT 0,
    completed_trade_count BIGINT NOT NULL DEFAULT 0,
    meso_volume BIGINT NOT NULL DEFAULT 0,
    vwap NUMERIC(24,6),
    minimum_price BIGINT,
    maximum_price BIGINT,
    npc_created_quantity BIGINT NOT NULL DEFAULT 0,
    farm_created_quantity BIGINT NOT NULL DEFAULT 0,
    npc_destroyed_quantity BIGINT NOT NULL DEFAULT 0,
    consumed_quantity BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, logical_date, item_id)
);

CREATE TABLE meso_flow_daily (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_date DATE NOT NULL,
    flow_kind TEXT NOT NULL,
    meso_amount BIGINT NOT NULL,
    transaction_count BIGINT NOT NULL,
    PRIMARY KEY (run_id, logical_date, flow_kind)
);

CREATE TABLE agent_state_projection (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    level INTEGER NOT NULL,
    experience BIGINT NOT NULL,
    meso BIGINT NOT NULL,
    map_id INTEGER NOT NULL,
    activity_state TEXT NOT NULL,
    stall_id TEXT,
    needs JSONB NOT NULL DEFAULT '{}',
    beliefs JSONB NOT NULL DEFAULT '{}',
    PRIMARY KEY (run_id, agent_id)
);
