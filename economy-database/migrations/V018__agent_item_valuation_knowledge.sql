CREATE TABLE item_valuation_query (
    valuation_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    agent_id VARCHAR(96) NOT NULL,
    logical_time TIMESTAMPTZ NOT NULL,
    item_id INTEGER NOT NULL CHECK (item_id > 0),
    unit_value_mesos BIGINT NOT NULL CHECK (unit_value_mesos >= 0),
    source VARCHAR(32) NOT NULL CHECK (source IN (
        'CUSTOM_OVERRIDE', 'PRIVATE_OBSERVATIONS', 'CATALOG_ANCHOR', 'UNKNOWN'
    )),
    observed_median_mesos BIGINT NOT NULL CHECK (observed_median_mesos >= 0),
    observation_count INTEGER NOT NULL CHECK (observation_count >= 0),
    catalog_anchor_mesos BIGINT NOT NULL CHECK (catalog_anchor_mesos >= 0),
    override_reason TEXT NOT NULL DEFAULT ''
);

CREATE INDEX item_valuation_query_agent_item_idx
    ON item_valuation_query(run_id, agent_id, item_id, logical_time);
CREATE INDEX item_valuation_query_item_time_idx
    ON item_valuation_query(run_id, item_id, logical_time);
