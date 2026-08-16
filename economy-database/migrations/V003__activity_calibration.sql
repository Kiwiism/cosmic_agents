CREATE TABLE activity_calibration_sample (
    sample_id UUID PRIMARY KEY,
    agent_character_id INTEGER NOT NULL,
    agent_build TEXT NOT NULL,
    map_id INTEGER NOT NULL,
    level INTEGER NOT NULL,
    job_family TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    kill_counts JSONB NOT NULL,
    consumed_items JSONB NOT NULL,
    died BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (completed_at > started_at)
);
CREATE INDEX activity_calibration_lookup_idx
    ON activity_calibration_sample(map_id, job_family, level, completed_at);
