-- Parameters: :baseline_run_id, :candidate_run_id, :from_logical_at, :to_logical_at
-- This reports measured differences. It does not label them causal without paired scenario design.
WITH selected_runs AS (
    SELECT 'baseline' label, CAST(:baseline_run_id AS uuid) run_id
    UNION ALL SELECT 'candidate', CAST(:candidate_run_id AS uuid)
), pairing AS (
    SELECT p.experiment_id, p.pair_id, p.seed, b.status baseline_status, c.status candidate_status
    FROM economy_experiment_pair p
    LEFT JOIN simulation_run b ON b.run_id = p.baseline_run_id
    LEFT JOIN simulation_run c ON c.run_id = p.candidate_run_id
    WHERE p.baseline_run_id = CAST(:baseline_run_id AS uuid)
      AND p.candidate_run_id = CAST(:candidate_run_id AS uuid)
), ending_meso AS (
    SELECT r.label, COALESCE(SUM(p.quantity) FILTER (
        WHERE p.account_type IN ('AGENT','HUMAN','ESCROW')), 0)::numeric value
    FROM selected_runs r
    LEFT JOIN economic_event e ON e.run_id = r.run_id AND e.logical_time <= :to_logical_at
    LEFT JOIN ledger_posting p ON p.event_id = e.event_id AND p.asset_type = 'MESO'
    GROUP BY r.label
), meso_flows AS (
    SELECT r.label,
           COALESCE(-SUM(p.quantity) FILTER (WHERE p.account_type = 'SOURCE' AND p.quantity < 0), 0)::numeric created,
           COALESCE(SUM(p.quantity) FILTER (WHERE p.account_type = 'SINK' AND p.quantity > 0), 0)::numeric destroyed,
           COALESCE(SUM(p.quantity) FILTER (WHERE p.account_type IN ('AGENT','HUMAN')
               AND p.quantity > 0 AND e.event_kind IN ('STALL_SALE','DIRECT_TRADE')), 0)::numeric trade_volume
    FROM selected_runs r
    LEFT JOIN economic_event e ON e.run_id = r.run_id
      AND e.logical_time BETWEEN :from_logical_at AND :to_logical_at
    LEFT JOIN ledger_posting p ON p.event_id = e.event_id AND p.asset_type = 'MESO'
    GROUP BY r.label
), market AS (
    SELECT r.label, COALESCE(SUM(m.completed_quantity), 0)::numeric completed_units,
           COALESCE(SUM(m.completed_trade_count), 0)::numeric completed_trades,
           COALESCE(SUM(m.meso_volume), 0)::numeric market_meso_volume
    FROM selected_runs r LEFT JOIN item_market_daily m ON m.run_id = r.run_id
      AND m.logical_date BETWEEN :from_logical_at::date AND :to_logical_at::date
    GROUP BY r.label
), other AS (
    SELECT r.label,
           (SELECT COUNT(*) FROM agent_demand d WHERE d.run_id = r.run_id AND d.status = 'OPEN')::numeric open_demands,
           (SELECT COUNT(*) FROM economy_invariant_violation v
             WHERE v.run_id = r.run_id AND v.resolved_at IS NULL)::numeric invariant_violations
    FROM selected_runs r
), metrics AS (
    SELECT r.label, jsonb_build_object(
        'endingMesoSupply', em.value,
        'mesoCreated', mf.created,
        'mesoDestroyed', mf.destroyed,
        'marketTransferVolume', mf.trade_volume,
        'completedUnits', m.completed_units,
        'completedTrades', m.completed_trades,
        'marketMesoVolume', m.market_meso_volume,
        'openDemands', o.open_demands,
        'unresolvedInvariantViolations', o.invariant_violations) value
    FROM selected_runs r JOIN ending_meso em USING (label)
    JOIN meso_flows mf USING (label) JOIN market m USING (label) JOIN other o USING (label)
), pair AS (
    SELECT (MAX(value::text) FILTER (WHERE label = 'baseline'))::jsonb baseline,
           (MAX(value::text) FILTER (WHERE label = 'candidate'))::jsonb candidate
    FROM metrics
)
SELECT jsonb_build_object(
    'pairDesign', COALESCE((SELECT jsonb_build_object('experimentId', experiment_id,
        'pairId', pair_id, 'seed', seed, 'baselineStatus', baseline_status,
        'candidateStatus', candidate_status) FROM pairing), 'null'::jsonb),
    'baseline', baseline,
    'candidate', candidate,
    'absoluteDelta', jsonb_build_object(
        'endingMesoSupply', (candidate->>'endingMesoSupply')::numeric - (baseline->>'endingMesoSupply')::numeric,
        'mesoCreated', (candidate->>'mesoCreated')::numeric - (baseline->>'mesoCreated')::numeric,
        'mesoDestroyed', (candidate->>'mesoDestroyed')::numeric - (baseline->>'mesoDestroyed')::numeric,
        'marketTransferVolume', (candidate->>'marketTransferVolume')::numeric - (baseline->>'marketTransferVolume')::numeric,
        'completedUnits', (candidate->>'completedUnits')::numeric - (baseline->>'completedUnits')::numeric,
        'completedTrades', (candidate->>'completedTrades')::numeric - (baseline->>'completedTrades')::numeric,
        'marketMesoVolume', (candidate->>'marketMesoVolume')::numeric - (baseline->>'marketMesoVolume')::numeric,
        'openDemands', (candidate->>'openDemands')::numeric - (baseline->>'openDemands')::numeric,
        'unresolvedInvariantViolations', (candidate->>'unresolvedInvariantViolations')::numeric
            - (baseline->>'unresolvedInvariantViolations')::numeric),
    'interpretation', CASE WHEN EXISTS (SELECT 1 FROM pairing
        WHERE baseline_status = 'COMPLETED' AND candidate_status = 'COMPLETED')
        THEN 'PAIRED_SAME_SEED_DIFFERENCE'
        ELSE 'MEASURED_DIFFERENCE_REQUIRES_PAIRED_DESIGN_FOR_CAUSAL_CLAIM' END
) AS scenario_comparison
FROM pair;
