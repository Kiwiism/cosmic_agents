CREATE TABLE agent_commerce_session_checkpoint (
    agent_id TEXT PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    request_id TEXT NOT NULL,
    session_id UUID,
    phase TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    checkpoint JSONB NOT NULL,
    CHECK (schema_version > 0),
    CHECK (length(trim(agent_id)) > 0),
    CHECK (length(trim(request_id)) > 0)
);

CREATE INDEX agent_commerce_session_checkpoint_phase_idx
    ON agent_commerce_session_checkpoint (phase, updated_at);
