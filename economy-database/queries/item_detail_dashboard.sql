-- Parameters: :run_id, :item_id
WITH postings AS (
    SELECT e.logical_time, e.event_kind, e.event_id, lp.account_type, lp.account_owner_id,
           lp.quantity, lp.lot_id, e.evidence
    FROM ledger_posting lp JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND lp.asset_type = 'ITEM'
      AND lp.asset_identifier = CAST(:item_id AS TEXT)
), holdings AS (
    SELECT account_type, account_owner_id, SUM(quantity) AS quantity
    FROM postings GROUP BY account_type, account_owner_id HAVING SUM(quantity) <> 0
), transactions AS (
    SELECT t.transaction_id, t.transaction_kind, t.logical_at, t.buyer_id, t.seller_id,
           GREATEST(
               COALESCE(SUM(p.quantity) FILTER (WHERE p.account_type IN ('AGENT','HUMAN')
                                                 AND p.quantity > 0), 0),
               COALESCE(-SUM(p.quantity) FILTER (WHERE p.account_type IN ('AGENT','HUMAN')
                                                  AND p.quantity < 0), 0)) AS quantity,
           t.gross_mesos, t.tax_mesos, t.human_counterparty, t.listing_id, t.evidence,
           e.actor_ids
    FROM economic_transaction t
    JOIN economic_event e ON e.event_id = t.committed_event_id
    JOIN ledger_posting p ON p.event_id = e.event_id
      AND p.asset_type = 'ITEM' AND p.asset_identifier = CAST(:item_id AS TEXT)
    WHERE t.run_id = :run_id
    GROUP BY t.run_id, t.transaction_id, t.transaction_kind, t.logical_at, t.buyer_id,
             t.seller_id, t.gross_mesos, t.tax_mesos, t.human_counterparty,
             t.listing_id, t.evidence, e.actor_ids
    ORDER BY t.logical_at
), asks AS (
    SELECT listing_id, seller_id, room_map_id, quantity_per_bundle, bundles_initial,
           bundles_remaining, bundle_price, opened_at, closed_at, close_reason, reprices
    FROM market_listing WHERE run_id = :run_id AND item_id = :item_id ORDER BY opened_at
), observations AS (
    SELECT logical_time, agent_id, room_map_id, listing_id, observed_state, unit_price,
           quantity, item_fingerprint, item_attributes
    FROM market_observation WHERE run_id = :run_id AND item_id = :item_id
    ORDER BY logical_time
), demand AS (
    SELECT demand_id, agent_id, demand_kind, required_quantity, maximum_willingness_to_pay,
           earliest_at, latest_at, status, evidence
    FROM agent_demand WHERE run_id = :run_id AND item_id = :item_id ORDER BY earliest_at
), decisions AS (
    SELECT decision_id, agent_id, logical_time, decision_kind, chosen_action, alternatives,
           beliefs_used, needs_used, utility_breakdown
    FROM decision_journal
    WHERE run_id = :run_id AND (
        jsonb_path_exists(chosen_action, '$.** ? (@.itemId == $id)',
                          jsonb_build_object('id', to_jsonb(CAST(:item_id AS INTEGER))))
        OR jsonb_path_exists(alternatives, '$.** ? (@.itemId == $id)',
                             jsonb_build_object('id', to_jsonb(CAST(:item_id AS INTEGER))))
        OR jsonb_path_exists(needs_used, '$.** ? (@.itemId == $id)',
                             jsonb_build_object('id', to_jsonb(CAST(:item_id AS INTEGER)))))
    ORDER BY logical_time
), exposures AS (
    SELECT x.* FROM listing_exposure x JOIN market_listing l USING (run_id, listing_id)
    WHERE x.run_id = :run_id AND l.item_id = :item_id
)
SELECT jsonb_build_object(
    'itemId', :item_id,
    'currentHoldings', COALESCE((SELECT jsonb_agg(to_jsonb(holdings)) FROM holdings), '[]'),
    'provenanceLots', COALESCE((SELECT jsonb_agg(to_jsonb(item_lot) ORDER BY lot_id)
                                FROM item_lot WHERE run_id = :run_id AND item_id = :item_id), '[]'),
    'transactions', COALESCE((SELECT jsonb_agg(to_jsonb(transactions)) FROM transactions), '[]'),
    'askHistory', COALESCE((SELECT jsonb_agg(to_jsonb(asks)) FROM asks), '[]'),
    'privateObservations', COALESCE((SELECT jsonb_agg(to_jsonb(observations)) FROM observations), '[]'),
    'listingLotAllocations', COALESCE((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.listing_id, a.lot_id)
        FROM market_listing_lot a JOIN market_listing l USING (run_id, listing_id)
        WHERE a.run_id = :run_id AND l.item_id = :item_id), '[]'),
    'exposures', COALESCE((SELECT jsonb_agg(to_jsonb(exposures)) FROM exposures), '[]'),
    'demand', COALESCE((SELECT jsonb_agg(to_jsonb(demand)) FROM demand), '[]'),
    'decisionReasons', COALESCE((SELECT jsonb_agg(to_jsonb(decisions)) FROM decisions), '[]'),
    'eventTrail', COALESCE((SELECT jsonb_agg(to_jsonb(postings) ORDER BY logical_time, event_id)
                            FROM postings), '[]')
) AS item_detail;
