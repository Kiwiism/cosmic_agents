-- Parameters: :run_id, :item_id
-- Result 1: run-relative daily price, volume, creation, destruction, and consumption.
SELECT logical_day, logical_date, completed_quantity, completed_trade_count, meso_volume,
       vwap, minimum_price, maximum_price, npc_created_quantity, farm_created_quantity,
       quest_created_quantity, transformed_created_quantity, npc_destroyed_quantity,
       consumed_quantity
FROM item_market_daily
WHERE run_id = :run_id AND item_id = :item_id
ORDER BY logical_day;

-- Result 2: every priced settlement. Evidence contains the exact server receipt and reason context.
SELECT transaction_id, transaction_kind, buyer_id, seller_id, quantity, gross_mesos,
       tax_mesos, human_counterparty, logical_at, listing_id, evidence
FROM economic_transaction
WHERE run_id = :run_id AND item_id = :item_id
ORDER BY logical_at, transaction_id;

-- Result 3: listing history and realized exposure.
SELECT l.listing_id, l.stall_id, l.seller_id, l.room_map_id, l.lot_id,
       l.quantity_per_bundle, l.bundles_initial, l.bundles_remaining, l.bundle_price,
       l.opened_at, l.closed_at, l.close_reason, l.reprices,
       COUNT(e.observer_id) AS distinct_observers,
       COALESCE(SUM(e.observation_count), 0) AS observation_count
FROM market_listing l
LEFT JOIN listing_exposure e ON e.run_id = l.run_id AND e.listing_id = l.listing_id
WHERE l.run_id = :run_id AND l.item_id = :item_id
GROUP BY l.run_id, l.listing_id
ORDER BY l.opened_at, l.listing_id;

-- Result 4: current/historic demand and explicit willingness-to-pay reasons.
SELECT demand_id, agent_id, demand_kind, required_quantity, marginal_utility,
       maximum_willingness_to_pay, earliest_at, latest_at, status, evidence
FROM agent_demand
WHERE run_id = :run_id AND item_id = :item_id
ORDER BY earliest_at, demand_id;

-- Result 5: provenance for every produced lot of this item.
SELECT lot_id, created_event_id, source_kind, source_identifier, original_quantity, attributes
FROM item_lot
WHERE run_id = :run_id AND item_id = :item_id
ORDER BY created_event_id, lot_id;
