-- Parameters: :run_id, :agent_id, :from_logical_at, :to_logical_at
SELECT d.decision_id, d.logical_time, d.decision_kind, d.chosen_action, d.alternatives,
       d.beliefs_used, d.needs_used, d.utility_breakdown, d.random_stream, d.random_draw,
       COALESCE((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.logical_time)
                 FROM social_event s
                 WHERE s.run_id = d.run_id AND s.speaker_agent_id = d.agent_id
                   AND s.logical_time BETWEEN d.logical_time - INTERVAL '5 minutes'
                                          AND d.logical_time + INTERVAL '5 minutes'), '[]') AS nearby_social,
       COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.logical_at)
                 FROM economic_transaction t
                 WHERE t.run_id = d.run_id
                   AND (t.buyer_id = d.agent_id OR t.seller_id = d.agent_id)
                   AND t.logical_at BETWEEN d.logical_time AND d.logical_time + INTERVAL '1 hour'), '[]')
                 AS realized_transactions
FROM decision_journal d
WHERE d.run_id = :run_id AND d.agent_id = :agent_id
  AND d.logical_time BETWEEN :from_logical_at AND :to_logical_at
ORDER BY d.logical_time, d.decision_id;
