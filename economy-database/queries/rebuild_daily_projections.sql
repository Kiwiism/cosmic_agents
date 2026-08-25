BEGIN;
DELETE FROM item_market_daily WHERE run_id = :run_id;
DELETE FROM meso_flow_daily WHERE run_id = :run_id;

INSERT INTO item_market_daily (
    run_id, logical_date, logical_day, item_id, completed_quantity, completed_trade_count,
    meso_volume, vwap, minimum_price, maximum_price, npc_created_quantity,
    farm_created_quantity, quest_created_quantity, transformed_created_quantity,
    npc_destroyed_quantity, consumed_quantity)
WITH item_flow AS (
    SELECT e.run_id,
           r.logical_started_at::date + FLOOR(EXTRACT(EPOCH FROM
               (e.logical_time - r.logical_started_at)) / 86400)::integer AS logical_date,
           1 + FLOOR(EXTRACT(EPOCH FROM (e.logical_time - r.logical_started_at)) / 86400)::integer AS logical_day,
           p.asset_identifier::integer AS item_id,
           SUM(CASE WHEN e.event_kind IN ('STALL_SALE','DIRECT_TRADE')
                    AND p.account_type IN ('AGENT','HUMAN') AND p.quantity > 0
                    THEN p.quantity ELSE 0 END) AS completed_quantity,
           COUNT(DISTINCT e.event_id) FILTER
                    (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')) AS completed_trade_count,
           -SUM(CASE WHEN p.account_type = 'SOURCE'
                    AND (p.account_owner_id LIKE 'NPC_STOCK:%'
                         OR p.account_owner_id LIKE 'NPC_RECHARGE:%')
                    AND p.quantity < 0 THEN p.quantity ELSE 0 END) AS npc_created_quantity,
           -SUM(CASE WHEN p.account_type = 'SOURCE' AND p.account_owner_id LIKE 'MOB:%'
                    AND p.quantity < 0 THEN p.quantity ELSE 0 END) AS farm_created_quantity,
           -SUM(CASE WHEN p.account_type = 'SOURCE' AND p.account_owner_id LIKE 'QUEST:%'
                    AND p.quantity < 0 THEN p.quantity ELSE 0 END) AS quest_created_quantity,
           -SUM(CASE WHEN p.account_type = 'SOURCE'
                    AND p.account_owner_id LIKE 'SCROLL_TRANSFORMATION:%'
                    AND p.quantity < 0 THEN p.quantity ELSE 0 END) AS transformed_created_quantity,
           SUM(CASE WHEN p.account_type = 'SINK' AND p.account_owner_id LIKE 'NPC_BUYBACK:%'
                    AND p.quantity > 0 THEN p.quantity ELSE 0 END) AS npc_destroyed_quantity,
           SUM(CASE WHEN p.account_type = 'SINK' AND p.quantity > 0 AND (
                    p.account_owner_id IN ('FARM_CONSUMPTION','SCROLL_CONSUMPTION','DEATH_SAFETY_CHARM')
                    OR p.account_owner_id LIKE 'QUEST_REQUIREMENT:%'
                    OR (p.account_owner_id = 'SCROLL_INPUT'
                        AND e.evidence->'scrollApplication'->>'outcome' = 'CURSE'))
                    THEN p.quantity ELSE 0 END) AS consumed_quantity
    FROM economic_event e
    JOIN simulation_run r ON r.run_id = e.run_id
    JOIN ledger_posting p ON p.event_id = e.event_id
    WHERE e.run_id = :run_id AND p.asset_type = 'ITEM'
    GROUP BY e.run_id, r.logical_started_at,
             FLOOR(EXTRACT(EPOCH FROM (e.logical_time - r.logical_started_at)) / 86400)::integer,
             p.asset_identifier::integer
), priced AS (
    SELECT t.run_id,
           r.logical_started_at::date + FLOOR(EXTRACT(EPOCH FROM
               (t.logical_at - r.logical_started_at)) / 86400)::integer AS logical_date,
           1 + FLOOR(EXTRACT(EPOCH FROM (t.logical_at - r.logical_started_at)) / 86400)::integer AS logical_day,
           t.item_id, SUM(t.gross_mesos) AS meso_volume,
           SUM(t.gross_mesos)::numeric / NULLIF(SUM(t.quantity), 0) AS vwap,
           MIN(t.gross_mesos / NULLIF(t.quantity, 0)) AS minimum_price,
           MAX(t.gross_mesos / NULLIF(t.quantity, 0)) AS maximum_price
    FROM economic_transaction t JOIN simulation_run r ON r.run_id = t.run_id
    WHERE t.run_id = :run_id
      AND t.transaction_kind IN ('PLAYER_SHOP_SALE','PLAYER_TRADE')
      AND t.item_id IS NOT NULL AND t.quantity > 0 AND t.gross_mesos IS NOT NULL
    GROUP BY t.run_id, r.logical_started_at,
             FLOOR(EXTRACT(EPOCH FROM (t.logical_at - r.logical_started_at)) / 86400)::integer,
             t.item_id
)
SELECT f.run_id, f.logical_date, f.logical_day, f.item_id,
       f.completed_quantity, f.completed_trade_count,
       COALESCE(p.meso_volume, 0), p.vwap, p.minimum_price, p.maximum_price,
       f.npc_created_quantity, f.farm_created_quantity, f.quest_created_quantity,
       f.transformed_created_quantity, f.npc_destroyed_quantity, f.consumed_quantity
FROM item_flow f LEFT JOIN priced p USING (run_id, logical_date, logical_day, item_id);

INSERT INTO meso_flow_daily(
    run_id, logical_date, logical_day, flow_kind, meso_amount, transaction_count)
SELECT e.run_id,
       r.logical_started_at::date + FLOOR(EXTRACT(EPOCH FROM
           (e.logical_time - r.logical_started_at)) / 86400)::integer,
       1 + FLOOR(EXTRACT(EPOCH FROM (e.logical_time - r.logical_started_at)) / 86400)::integer,
       CASE WHEN p.account_type = 'SOURCE' THEN 'CREATED:' || p.account_owner_id
            ELSE 'DESTROYED:' || p.account_owner_id END,
       SUM(ABS(p.quantity)), COUNT(DISTINCT e.event_id)
FROM economic_event e
JOIN simulation_run r ON r.run_id = e.run_id
JOIN ledger_posting p ON p.event_id = e.event_id
WHERE e.run_id = :run_id AND p.asset_type = 'MESO'
  AND p.account_type IN ('SOURCE','SINK')
GROUP BY e.run_id, r.logical_started_at,
         FLOOR(EXTRACT(EPOCH FROM (e.logical_time - r.logical_started_at)) / 86400)::integer,
         p.account_type, p.account_owner_id;
COMMIT;
