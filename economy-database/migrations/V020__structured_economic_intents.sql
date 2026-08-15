CREATE TABLE economic_intent (
    intent_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    actor_agent_id VARCHAR(96) NOT NULL,
    counterparty_agent_id VARCHAR(96),
    kind VARCHAR(32) NOT NULL CHECK (kind IN (
        'BUY_INTEREST','SELL_INTEREST','MESO_OFFER','COUNTER_OFFER','ACCEPT','REJECT'
    )),
    item_id INTEGER NOT NULL CHECK (item_id > 0),
    item_fingerprint VARCHAR(256),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    mesos BIGINT NOT NULL CHECK (mesos >= 0),
    preferred_map_id INTEGER,
    public_text TEXT NOT NULL DEFAULT '',
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'OPEN','ACCEPTED','REJECTED','EXPIRED','CANCELLED','SETTLED'
    )),
    resolved_at TIMESTAMPTZ,
    resolution_reason TEXT,
    CHECK (counterparty_agent_id IS NULL OR counterparty_agent_id <> actor_agent_id),
    CHECK (expires_at > created_at)
);

CREATE INDEX economic_intent_discovery_idx
    ON economic_intent(run_id, item_id, status, created_at, expires_at);
CREATE INDEX economic_intent_counterparty_idx
    ON economic_intent(run_id, counterparty_agent_id, status, created_at);
