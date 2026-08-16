-- Dashboard contract: completed trades and reasons for one item, chronologically.
SELECT e.logical_time,
       e.event_kind,
       e.actor_ids,
       e.evidence,
       p.account_type,
       p.account_owner_id,
       p.quantity,
       p.lot_id
FROM economic_event e
JOIN ledger_posting p ON p.event_id = e.event_id
WHERE e.run_id = :run_id
  AND p.asset_type = 'ITEM'
  AND p.asset_identifier = CAST(:item_id AS TEXT)
ORDER BY e.logical_time, e.event_id, p.posting_index;
