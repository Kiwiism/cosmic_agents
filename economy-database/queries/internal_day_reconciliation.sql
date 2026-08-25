-- Parameter: :run_id
-- Clean closes are immutable evidence that each completed internal day reconciled.
SELECT c.day_index, c.day_started_at, c.day_closed_at, c.checkpoint_hash,
       c.relayed_count, c.relay_failure_count, c.ingested_count, c.quarantined_count,
       c.audit_clean, c.violation_count,
       COALESCE((SELECT SUM(m.meso_amount) FROM meso_flow_daily m
                 WHERE m.run_id = c.run_id AND m.logical_day = c.day_index
                   AND m.flow_kind LIKE 'CREATED:%'), 0) AS meso_created,
       COALESCE((SELECT SUM(m.meso_amount) FROM meso_flow_daily m
                 WHERE m.run_id = c.run_id AND m.logical_day = c.day_index
                   AND m.flow_kind LIKE 'DESTROYED:%'), 0) AS meso_destroyed,
       COALESCE((SELECT SUM(i.meso_volume) FROM item_market_daily i
                 WHERE i.run_id = c.run_id AND i.logical_day = c.day_index), 0) AS market_meso_volume,
       COALESCE((SELECT SUM(i.completed_trade_count) FROM item_market_daily i
                 WHERE i.run_id = c.run_id AND i.logical_day = c.day_index), 0) AS completed_trades
FROM economy_day_close c
WHERE c.run_id = :run_id
ORDER BY c.day_index;
