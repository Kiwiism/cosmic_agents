CREATE TABLE social_schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO social_schema_version(version) VALUES (1);

CREATE TABLE agent_relationship_memory (
    agent_id INTEGER NOT NULL,
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('AGENT', 'PLAYER')),
    target_id INTEGER NOT NULL,
    familiarity DOUBLE PRECISION NOT NULL CHECK (familiarity BETWEEN 0.0 AND 1.0),
    trust DOUBLE PRECISION NOT NULL CHECK (trust BETWEEN 0.0 AND 1.0),
    affinity DOUBLE PRECISION NOT NULL CHECK (affinity BETWEEN 0.0 AND 1.0),
    annoyance DOUBLE PRECISION NOT NULL CHECK (annoyance BETWEEN 0.0 AND 1.0),
    interaction_count BIGINT NOT NULL CHECK (interaction_count >= 0),
    summary VARCHAR(512) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    last_interaction_at_ms BIGINT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    PRIMARY KEY (agent_id, target_type, target_id),
    CHECK (agent_id > 0 AND target_id > 0 AND agent_id <> target_id)
);

CREATE INDEX agent_relationship_recent_idx
    ON agent_relationship_memory(agent_id, last_interaction_at_ms DESC);

CREATE TABLE agent_conversation_turn (
    turn_id BIGSERIAL PRIMARY KEY,
    agent_id INTEGER NOT NULL,
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('AGENT', 'PLAYER')),
    target_id INTEGER NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('HUMAN', 'AGENT')),
    speaker_id INTEGER NOT NULL,
    speaker_name VARCHAR(64) NOT NULL,
    text VARCHAR(512) NOT NULL,
    occurred_at_ms BIGINT NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    CHECK (agent_id > 0 AND target_id > 0 AND speaker_id > 0),
    CHECK (expires_at_ms >= occurred_at_ms)
);

CREATE INDEX agent_conversation_recent_idx
    ON agent_conversation_turn(agent_id, target_type, target_id, occurred_at_ms DESC);
CREATE INDEX agent_conversation_expiry_idx
    ON agent_conversation_turn(expires_at_ms);

CREATE TABLE agent_memory_event (
    event_id VARCHAR(128) PRIMARY KEY,
    agent_id INTEGER NOT NULL,
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('AGENT', 'PLAYER')),
    target_id INTEGER NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    salience DOUBLE PRECISION NOT NULL CHECK (salience BETWEEN 0.0 AND 1.0),
    occurred_at_ms BIGINT NOT NULL,
    expires_at_ms BIGINT,
    source_event_id VARCHAR(128),
    CHECK (agent_id > 0 AND target_id > 0)
);

CREATE INDEX agent_memory_event_lookup_idx
    ON agent_memory_event(agent_id, target_type, target_id, occurred_at_ms DESC);
CREATE INDEX agent_memory_event_expiry_idx
    ON agent_memory_event(expires_at_ms) WHERE expires_at_ms IS NOT NULL;
