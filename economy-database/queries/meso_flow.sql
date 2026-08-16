SELECT e.logical_time::date AS logical_date,
       p.account_type,
       p.account_owner_id,
       SUM(p.quantity) AS net_meso,
       SUM(CASE WHEN p.quantity > 0 THEN p.quantity ELSE 0 END) AS inflow,
       -SUM(CASE WHEN p.quantity < 0 THEN p.quantity ELSE 0 END) AS outflow,
       COUNT(DISTINCT e.event_id) AS event_count
FROM economic_event e
JOIN ledger_posting p ON p.event_id = e.event_id
WHERE e.run_id = :run_id
  AND p.asset_type = 'MESO'
GROUP BY e.logical_time::date, p.account_type, p.account_owner_id
ORDER BY logical_date, p.account_type, p.account_owner_id;
