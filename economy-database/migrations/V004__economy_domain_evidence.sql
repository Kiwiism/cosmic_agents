CREATE TABLE economy_config_revision (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    revision INTEGER NOT NULL,
    effective_logical_at TIMESTAMPTZ NOT NULL,
    config_hash CHAR(64) NOT NULL,
    config_yaml TEXT NOT NULL,
    normalized_config JSONB NOT NULL,
    reason TEXT NOT NULL,
    PRIMARY KEY (run_id, revision),
    UNIQUE (run_id, effective_logical_at)
);

CREATE TABLE population_arrival (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    logical_at TIMESTAMPTZ NOT NULL,
    arrival_kind TEXT NOT NULL,
    PRIMARY KEY (run_id, agent_id)
);

CREATE TABLE agent_economic_profile (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    job_family TEXT NOT NULL,
    daily_activity_fraction DOUBLE PRECISION NOT NULL CHECK (daily_activity_fraction BETWEEN 0 AND 1),
    risk_tolerance DOUBLE PRECISION NOT NULL CHECK (risk_tolerance BETWEEN 0 AND 1),
    liquidity_preference DOUBLE PRECISION NOT NULL CHECK (liquidity_preference BETWEEN 0 AND 1),
    upgrade_aggressiveness DOUBLE PRECISION NOT NULL CHECK (upgrade_aggressiveness BETWEEN 0 AND 1),
    shopping_patience DOUBLE PRECISION NOT NULL CHECK (shopping_patience BETWEEN 0 AND 1),
    stall_willingness DOUBLE PRECISION NOT NULL CHECK (stall_willingness BETWEEN 0 AND 1),
    price_memory_hours INTEGER NOT NULL CHECK (price_memory_hours > 0),
    negotiation_aggressiveness DOUBLE PRECISION NOT NULL CHECK (negotiation_aggressiveness BETWEEN 0 AND 1),
    chair_interest DOUBLE PRECISION NOT NULL CHECK (chair_interest BETWEEN 0 AND 1),
    sampled_profile JSONB NOT NULL,
    PRIMARY KEY (run_id, agent_id)
);

CREATE TABLE agent_lifecycle_state (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('IN_FREE_MARKET','OFFSCREEN_ACTIVITY','RETURNING_TO_FM')),
    logical_at TIMESTAMPTZ NOT NULL,
    activity_id TEXT,
    PRIMARY KEY (run_id, agent_id),
    FOREIGN KEY (run_id, agent_id) REFERENCES agent_economic_profile(run_id, agent_id)
);

CREATE TABLE activity_session (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    activity_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    calibration_id TEXT NOT NULL,
    map_id INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status TEXT NOT NULL CHECK (status IN ('STARTED','COMPLETED','FAILED')),
    explicit_work JSONB NOT NULL,
    outcome JSONB,
    PRIMARY KEY (run_id, activity_id),
    FOREIGN KEY (run_id, agent_id) REFERENCES agent_economic_profile(run_id, agent_id)
);
CREATE INDEX activity_session_agent_time_idx ON activity_session(run_id, agent_id, started_at);

CREATE TABLE economic_transaction (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    transaction_id TEXT NOT NULL,
    committed_event_id UUID NOT NULL REFERENCES economic_event(event_id),
    transaction_kind TEXT NOT NULL,
    buyer_id TEXT,
    seller_id TEXT,
    item_id INTEGER,
    quantity INTEGER,
    gross_mesos BIGINT,
    tax_mesos BIGINT,
    human_counterparty BOOLEAN NOT NULL DEFAULT FALSE,
    logical_at TIMESTAMPTZ NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}',
    PRIMARY KEY (run_id, transaction_id)
);
CREATE INDEX economic_transaction_item_time_idx ON economic_transaction(run_id, item_id, logical_at);

CREATE TABLE item_lot (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    lot_id TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    created_event_id UUID NOT NULL REFERENCES economic_event(event_id),
    source_kind TEXT NOT NULL,
    source_identifier TEXT NOT NULL,
    original_quantity BIGINT NOT NULL CHECK (original_quantity > 0),
    attributes JSONB NOT NULL DEFAULT '{}',
    PRIMARY KEY (run_id, lot_id)
);
CREATE INDEX item_lot_item_source_idx ON item_lot(run_id, item_id, source_kind);

CREATE TABLE item_instance (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    instance_id TEXT NOT NULL,
    lot_id TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    equipment_stats JSONB NOT NULL,
    current_owner_id TEXT,
    current_location TEXT NOT NULL,
    destroyed_event_id UUID REFERENCES economic_event(event_id),
    PRIMARY KEY (run_id, instance_id),
    FOREIGN KEY (run_id, lot_id) REFERENCES item_lot(run_id, lot_id)
);

CREATE TABLE market_stall (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    stall_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    room_map_id INTEGER NOT NULL,
    spot_x INTEGER NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    close_reason TEXT,
    PRIMARY KEY (run_id, stall_id)
);
CREATE UNIQUE INDEX one_open_stall_per_agent_idx ON market_stall(run_id, seller_id)
    WHERE closed_at IS NULL;
CREATE UNIQUE INDEX one_open_stall_per_spot_idx ON market_stall(run_id, room_map_id, spot_x)
    WHERE closed_at IS NULL;

CREATE TABLE market_listing (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    listing_id TEXT NOT NULL,
    stall_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    room_map_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    lot_id TEXT NOT NULL,
    quantity_per_bundle INTEGER NOT NULL CHECK (quantity_per_bundle > 0),
    bundles_initial INTEGER NOT NULL CHECK (bundles_initial > 0),
    bundles_remaining INTEGER NOT NULL CHECK (bundles_remaining >= 0),
    bundle_price BIGINT NOT NULL CHECK (bundle_price > 0),
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    close_reason TEXT,
    reprices INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, listing_id),
    FOREIGN KEY (run_id, stall_id) REFERENCES market_stall(run_id, stall_id),
    FOREIGN KEY (run_id, lot_id) REFERENCES item_lot(run_id, lot_id)
);
CREATE INDEX market_listing_item_time_idx ON market_listing(run_id, item_id, opened_at);

CREATE TABLE listing_exposure (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    listing_id TEXT NOT NULL,
    observer_id TEXT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    observation_count INTEGER NOT NULL CHECK (observation_count > 0),
    PRIMARY KEY (run_id, listing_id, observer_id),
    FOREIGN KEY (run_id, listing_id) REFERENCES market_listing(run_id, listing_id)
);

CREATE TABLE market_belief (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    logical_at TIMESTAMPTZ NOT NULL,
    estimate JSONB NOT NULL,
    evidence_observation_ids JSONB NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, agent_id, item_id)
);

CREATE TABLE agent_demand (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    demand_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    item_id INTEGER,
    item_predicate JSONB,
    demand_kind TEXT NOT NULL,
    required_quantity INTEGER NOT NULL CHECK (required_quantity > 0),
    marginal_utility DOUBLE PRECISION NOT NULL,
    maximum_willingness_to_pay BIGINT NOT NULL CHECK (maximum_willingness_to_pay >= 0),
    earliest_at TIMESTAMPTZ NOT NULL,
    latest_at TIMESTAMPTZ,
    status TEXT NOT NULL,
    evidence JSONB NOT NULL,
    PRIMARY KEY (run_id, demand_id),
    CHECK ((item_id IS NULL) <> (item_predicate IS NULL))
);
CREATE INDEX agent_demand_item_status_idx ON agent_demand(run_id, item_id, status);

CREATE TABLE negotiation_session (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    negotiation_id TEXT NOT NULL,
    buyer_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    status TEXT NOT NULL,
    transcript JSONB NOT NULL,
    settlement_transaction_id TEXT,
    PRIMARY KEY (run_id, negotiation_id)
);

CREATE TABLE economy_invariant_violation (
    violation_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_at TIMESTAMPTZ NOT NULL,
    invariant_code TEXT NOT NULL,
    severity TEXT NOT NULL,
    related_event_id UUID REFERENCES economic_event(event_id),
    evidence JSONB NOT NULL,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX economy_invariant_open_idx ON economy_invariant_violation(run_id, resolved_at, logical_at);

CREATE TABLE market_snapshot (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    logical_at TIMESTAMPTZ NOT NULL,
    projection_version INTEGER NOT NULL,
    metrics JSONB NOT NULL,
    source_event_high_watermark UUID,
    PRIMARY KEY (run_id, logical_at, projection_version)
);
