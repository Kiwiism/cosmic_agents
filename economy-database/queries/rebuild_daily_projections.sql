BEGIN;
DELETE FROM item_market_daily WHERE run_id = :run_id;
DELETE FROM meso_flow_daily WHERE run_id = :run_id;

INSERT INTO item_market_daily (
    run_id, logical_date, item_id, completed_quantity, completed_trade_count, meso_volume,
    vwap, minimum_price, maximum_price, npc_created_quantity, farm_created_quantity,
    npc_destroyed_quantity, consumed_quantity)
SELECT e.run_id, e.logical_time::date, p.asset_identifier::integer,
       SUM(CASE WHEN e.event_kind IN ('STALL_SALE','DIRECT_TRADE')
                 AND p.account_type = 'AGENT' AND p.quantity > 0 THEN p.quantity ELSE 0 END),
       COUNT(DISTINCT e.event_id) FILTER (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')),
       SUM(CASE WHEN e.event_kind IN ('STALL_SALE','DIRECT_TRADE')
                THEN COALESCE((e.evidence->>'grossMesos')::bigint,
                              (e.evidence->>'gross')::bigint, 0) ELSE 0 END),
       AVG(COALESCE((e.evidence->>'grossMesos')::numeric, (e.evidence->>'gross')::numeric)
               / NULLIF((e.evidence->>'quantity')::numeric, 0))
           FILTER (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')),
       MIN((COALESCE((e.evidence->>'grossMesos')::bigint, (e.evidence->>'gross')::bigint)
               / NULLIF((e.evidence->>'quantity')::bigint, 0)))
           FILTER (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')),
       MAX((COALESCE((e.evidence->>'grossMesos')::bigint, (e.evidence->>'gross')::bigint)
               / NULLIF((e.evidence->>'quantity')::bigint, 0)))
           FILTER (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')),
       SUM(CASE WHEN e.event_kind = 'NPC_PURCHASE' AND p.account_type = 'AGENT' AND p.quantity > 0
                THEN p.quantity ELSE 0 END),
       SUM(CASE WHEN e.event_kind = 'FARM_RESULT' AND p.account_type = 'AGENT' AND p.quantity > 0
                THEN p.quantity ELSE 0 END),
       -SUM(CASE WHEN e.event_kind = 'NPC_SALE' AND p.account_type = 'AGENT' AND p.quantity < 0
                 THEN p.quantity ELSE 0 END),
       -SUM(CASE WHEN e.event_kind IN ('CONSUMPTION','SCROLL_APPLIED','QUEST_TURN_IN')
                  AND p.account_type = 'AGENT' AND p.quantity < 0 THEN p.quantity ELSE 0 END)
FROM economic_event e
JOIN ledger_posting p ON p.event_id = e.event_id AND p.asset_type = 'ITEM'
WHERE e.run_id = :run_id
GROUP BY e.run_id, e.logical_time::date, p.asset_identifier::integer;

INSERT INTO meso_flow_daily(run_id, logical_date, flow_kind, meso_amount, transaction_count)
SELECT e.run_id, e.logical_time::date,
       CASE WHEN p.account_type = 'SOURCE' THEN 'CREATED:' || p.account_owner_id
            WHEN p.account_type = 'SINK' THEN 'DESTROYED:' || p.account_owner_id
            ELSE 'TRANSFER' END,
       SUM(ABS(p.quantity)), COUNT(DISTINCT e.event_id)
FROM economic_event e JOIN ledger_posting p ON p.event_id = e.event_id
WHERE e.run_id = :run_id AND p.asset_type = 'MESO'
  AND p.account_type IN ('SOURCE','SINK')
GROUP BY e.run_id, e.logical_time::date, flow_kind;
COMMIT;
