-- Parameters: :run_id, :from_logical_at, :to_logical_at
WITH meso AS (
    SELECT lp.account_type, lp.account_owner_id, SUM(lp.quantity) AS balance
    FROM ledger_posting lp
    JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND lp.asset_type = 'MESO'
      AND e.logical_time <= :to_logical_at
    GROUP BY lp.account_type, lp.account_owner_id
), flows AS (
    SELECT date_trunc('day', e.logical_time) AS logical_day,
           lp.account_type, lp.account_owner_id,
           SUM(lp.quantity) AS amount, COUNT(DISTINCT e.event_id) AS transactions
    FROM ledger_posting lp JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND lp.asset_type = 'MESO'
      AND e.logical_time BETWEEN :from_logical_at AND :to_logical_at
    GROUP BY 1, 2, 3
)
SELECT jsonb_build_object(
    'walletMeso', COALESCE((SELECT SUM(balance) FROM meso WHERE account_type = 'AGENT'), 0),
    'escrowMeso', COALESCE((SELECT SUM(balance) FROM meso WHERE account_type = 'ESCROW'), 0),
    'faucets', COALESCE((SELECT jsonb_object_agg(account_owner_id, -balance)
                         FROM meso WHERE account_type = 'SOURCE'), '{}'),
    'sinks', COALESCE((SELECT jsonb_object_agg(account_owner_id, balance)
                       FROM meso WHERE account_type = 'SINK'), '{}'),
    'flows', COALESCE((SELECT jsonb_agg(to_jsonb(flows) ORDER BY logical_day) FROM flows), '[]'),
    'activeStalls', (SELECT COUNT(*) FROM market_stall WHERE run_id = :run_id AND closed_at IS NULL),
    'openDemand', (SELECT COUNT(*) FROM agent_demand WHERE run_id = :run_id AND status = 'OPEN'),
    'invariantViolations', (SELECT COUNT(*) FROM economy_invariant_violation
                            WHERE run_id = :run_id AND resolved_at IS NULL)
) AS economy_overview;
