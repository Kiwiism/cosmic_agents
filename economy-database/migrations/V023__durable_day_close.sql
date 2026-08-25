ALTER TABLE simulation_run DROP CONSTRAINT simulation_run_status_check;

ALTER TABLE simulation_run ADD CONSTRAINT simulation_run_status_check CHECK (status IN (
    'CREATED', 'RUNNING', 'WAITING_PHYSICAL_ACTION', 'DAY_CLOSED', 'DAY_CLOSE_BLOCKED',
    'INVARIANT_VIOLATION',
    'COMPLETED', 'FAILED', 'STOPPED'
));

CREATE TABLE economy_day_close (
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    day_index INTEGER NOT NULL CHECK (day_index > 0),
    day_started_at TIMESTAMPTZ NOT NULL,
    day_closed_at TIMESTAMPTZ NOT NULL,
    checkpoint_hash CHAR(64) NOT NULL,
    relayed_count INTEGER NOT NULL CHECK (relayed_count >= 0),
    relay_failure_count INTEGER NOT NULL CHECK (relay_failure_count >= 0),
    ingested_count INTEGER NOT NULL CHECK (ingested_count >= 0),
    quarantined_count INTEGER NOT NULL CHECK (quarantined_count >= 0),
    audit_clean BOOLEAN NOT NULL,
    violation_count INTEGER NOT NULL CHECK (violation_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, day_index),
    CHECK (day_closed_at > day_started_at)
);

ALTER TABLE item_market_daily
    ADD COLUMN logical_day INTEGER NOT NULL DEFAULT 1 CHECK (logical_day > 0);
ALTER TABLE meso_flow_daily
    ADD COLUMN logical_day INTEGER NOT NULL DEFAULT 1 CHECK (logical_day > 0);

UPDATE item_market_daily d SET logical_day = 1 + (d.logical_date - r.logical_started_at::date)
FROM simulation_run r WHERE r.run_id = d.run_id;
UPDATE meso_flow_daily d SET logical_day = 1 + (d.logical_date - r.logical_started_at::date)
FROM simulation_run r WHERE r.run_id = d.run_id;

ALTER TABLE item_market_daily DROP CONSTRAINT item_market_daily_pkey;
ALTER TABLE item_market_daily ADD PRIMARY KEY (run_id, logical_day, item_id);
ALTER TABLE meso_flow_daily DROP CONSTRAINT meso_flow_daily_pkey;
ALTER TABLE meso_flow_daily ADD PRIMARY KEY (run_id, logical_day, flow_kind);

CREATE INDEX item_market_daily_run_day_idx ON item_market_daily(run_id, logical_day, item_id);
CREATE INDEX meso_flow_daily_run_day_idx ON meso_flow_daily(run_id, logical_day, flow_kind);
