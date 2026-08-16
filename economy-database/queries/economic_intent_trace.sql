-- Parameters: :run_id UUID, :agent_id TEXT, :item_id INTEGER (use 0 for every item)
SELECT jsonb_build_object(
    'intents', COALESCE(jsonb_agg(jsonb_build_object(
        'intentId', intent_id,
        'actorAgentId', actor_agent_id,
        'counterpartyAgentId', counterparty_agent_id,
        'kind', kind,
        'itemId', item_id,
        'itemFingerprint', item_fingerprint,
        'quantity', quantity,
        'mesos', mesos,
        'preferredMapId', preferred_map_id,
        'publicText', public_text,
        'attributes', attributes,
        'createdAt', created_at,
        'expiresAt', expires_at,
        'status', status,
        'resolvedAt', resolved_at,
        'resolutionReason', resolution_reason
    ) ORDER BY created_at, intent_id), '[]'::jsonb)
) AS economic_intent_trace
FROM economic_intent
WHERE run_id = :run_id
  AND (actor_agent_id = :agent_id OR counterparty_agent_id = :agent_id)
  AND (:item_id = 0 OR item_id = :item_id);
