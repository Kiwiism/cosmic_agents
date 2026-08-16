-- Parameters: :run_id
WITH event_balance AS (
    SELECT e.event_id, lp.asset_type, lp.asset_identifier, SUM(lp.quantity) AS imbalance
    FROM economic_event e JOIN ledger_posting lp USING (event_id)
    WHERE e.run_id = :run_id
    GROUP BY e.event_id, lp.asset_type, lp.asset_identifier
    HAVING SUM(lp.quantity) <> 0
), negative_holdings AS (
    SELECT lp.account_type, lp.account_owner_id, lp.asset_type, lp.asset_identifier,
           SUM(lp.quantity) AS balance
    FROM economic_event e JOIN ledger_posting lp USING (event_id)
    WHERE e.run_id = :run_id AND lp.account_type IN ('AGENT','ESCROW')
    GROUP BY lp.account_type, lp.account_owner_id, lp.asset_type, lp.asset_identifier
    HAVING SUM(lp.quantity) < 0
), duplicate_open_stalls AS (
    SELECT seller_id, COUNT(*) AS open_stalls FROM market_stall
    WHERE run_id = :run_id AND closed_at IS NULL GROUP BY seller_id HAVING COUNT(*) > 1
)
SELECT jsonb_build_object(
    'unbalancedEvents', COALESCE((SELECT jsonb_agg(to_jsonb(event_balance)) FROM event_balance), '[]'),
    'negativeHoldings', COALESCE((SELECT jsonb_agg(to_jsonb(negative_holdings)) FROM negative_holdings), '[]'),
    'duplicateOpenStalls', COALESCE((SELECT jsonb_agg(to_jsonb(duplicate_open_stalls))
                                     FROM duplicate_open_stalls), '[]'),
    'recordedViolations', COALESCE((SELECT jsonb_agg(to_jsonb(v) ORDER BY logical_at)
                                    FROM economy_invariant_violation v
                                    WHERE run_id = :run_id), '[]')
) AS invariant_audit;
