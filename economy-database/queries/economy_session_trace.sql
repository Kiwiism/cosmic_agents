-- Parameters: :run_id UUID, :agent_id TEXT
SELECT jsonb_build_object(
    'sessions', COALESCE(jsonb_agg(jsonb_build_object(
        'eventId', session_event_id,
        'requestId', request_id,
        'sessionId', session_id,
        'eventKind', event_kind,
        'logicalTime', logical_at,
        'reason', reason,
        'retryAt', retry_at,
        'expiresAt', expires_at
    ) ORDER BY logical_at, session_event_id), '[]'::jsonb)
) AS economy_session_trace
FROM economy_session_event
WHERE run_id = :run_id AND agent_id = :agent_id;
