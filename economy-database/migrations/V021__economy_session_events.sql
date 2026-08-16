CREATE TABLE economy_session_event (
    session_event_id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_run(run_id),
    request_id UUID,
    session_id UUID,
    agent_id VARCHAR(96) NOT NULL,
    event_kind VARCHAR(32) NOT NULL CHECK (event_kind IN (
        'ENTRY_ACCEPTED','ENTRY_DEFERRED','ENTRY_REJECTED',
        'RELEASE_RELEASED','RELEASE_DEFERRED','RELEASE_REJECTED'
    )),
    logical_at TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    retry_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE INDEX economy_session_event_agent_time_idx
    ON economy_session_event(run_id, agent_id, logical_at, session_event_id);
CREATE INDEX economy_session_event_session_idx
    ON economy_session_event(run_id, session_id, logical_at)
    WHERE session_id IS NOT NULL;
