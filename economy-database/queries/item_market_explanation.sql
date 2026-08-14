WITH trades AS (
    SELECT e.event_id, e.logical_time, e.event_kind, e.actor_ids, e.evidence,
           SUM(CASE WHEN p.account_type = 'AGENT' AND p.quantity > 0 THEN p.quantity ELSE 0 END) quantity
    FROM economic_event e
    JOIN ledger_posting p ON p.event_id = e.event_id
    WHERE e.run_id = :run_id AND p.asset_type = 'ITEM'
      AND p.asset_identifier = CAST(:item_id AS TEXT)
    GROUP BY e.event_id
), observations AS (
    SELECT logical_time, agent_id, room_map_id, unit_price, quantity, observed_state,
           item_fingerprint, item_attributes, listing_id
    FROM market_observation
    WHERE run_id = :run_id AND item_id = :item_id
)
SELECT 'TRANSACTION' record_type, logical_time, event_kind detail, actor_ids subjects,
       evidence payload, quantity
FROM trades
UNION ALL
SELECT 'OBSERVATION', logical_time, observed_state, to_jsonb(ARRAY[agent_id]),
       jsonb_build_object('roomMapId', room_map_id, 'unitPrice', unit_price), quantity
FROM observations
ORDER BY logical_time;
