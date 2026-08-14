-- Parameters: :run_id, :from_logical_at, :to_logical_at
-- Administrative projection only. Agents must never read this global view.
WITH wallet_balances AS (
    SELECT p.account_type, p.account_owner_id, SUM(p.quantity)::numeric AS balance
    FROM ledger_posting p JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND p.asset_type = 'MESO'
      AND p.account_type IN ('AGENT','HUMAN') AND e.logical_time <= :to_logical_at
    GROUP BY p.account_type, p.account_owner_id
), ranked_wealth AS (
    SELECT account_type, account_owner_id, GREATEST(balance, 0) balance,
           row_number() OVER (ORDER BY GREATEST(balance, 0), account_owner_id) rn,
           count(*) OVER () n
    FROM wallet_balances
), wealth AS (
    SELECT COUNT(*) participant_count, COALESCE(SUM(balance), 0) total_wallet_meso,
           COALESCE(AVG(balance), 0) mean_wallet_meso,
           COALESCE(percentile_cont(.5) WITHIN GROUP (ORDER BY balance), 0) median_wallet_meso,
           COALESCE(percentile_cont(.9) WITHIN GROUP (ORDER BY balance), 0) p90_wallet_meso,
           COALESCE(SUM((2 * rn - n - 1) * balance) / NULLIF(MAX(n) * SUM(balance), 0), 0) gini
    FROM ranked_wealth
), money_locations AS (
    SELECT p.account_type, SUM(p.quantity)::numeric balance
    FROM ledger_posting p JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND p.asset_type = 'MESO' AND e.logical_time <= :to_logical_at
      AND p.account_type IN ('AGENT','HUMAN','ESCROW')
    GROUP BY p.account_type
), transfer_volume AS (
    SELECT COALESCE(SUM(p.quantity) FILTER (WHERE p.quantity > 0
             AND p.account_type IN ('AGENT','HUMAN')), 0)::numeric volume
    FROM ledger_posting p JOIN economic_event e USING (event_id)
    WHERE e.run_id = :run_id AND p.asset_type = 'MESO'
      AND e.event_kind IN ('STALL_SALE','DIRECT_TRADE')
      AND e.logical_time BETWEEN :from_logical_at AND :to_logical_at
), seller_sales AS (
    SELECT seller_id, SUM(COALESCE(gross_mesos, 0))::numeric sales
    FROM economic_transaction
    WHERE run_id = :run_id AND transaction_kind = 'PLAYER_SHOP_SALE'
      AND seller_id IS NOT NULL AND logical_at BETWEEN :from_logical_at AND :to_logical_at
    GROUP BY seller_id
), seller_shares AS (
    SELECT seller_id, sales, sales / NULLIF(SUM(sales) OVER (), 0) share
    FROM seller_sales
), seller_concentration AS (
    SELECT COUNT(*) active_sellers, COALESCE(SUM(sales), 0) gross_sales,
           COALESCE(SUM(power(share, 2)), 0) seller_hhi
    FROM seller_shares
), stall_time AS (
    SELECT COALESCE(SUM(EXTRACT(EPOCH FROM
               LEAST(COALESCE(closed_at, :to_logical_at), :to_logical_at)
               - GREATEST(opened_at, :from_logical_at))), 0) occupied_room_seconds,
           COUNT(*) stalls_opened
    FROM market_stall
    WHERE run_id = :run_id AND opened_at < :to_logical_at
      AND COALESCE(closed_at, :to_logical_at) > :from_logical_at
), room_traffic AS (
    SELECT map_id, COUNT(*) presence_events, COUNT(DISTINCT agent_id) unique_agents
    FROM agent_presence_event
    WHERE run_id = :run_id AND visible AND map_id BETWEEN 910000000 AND 910000022
      AND logical_at BETWEEN :from_logical_at AND :to_logical_at
    GROUP BY map_id
), disposition AS (
    SELECT transaction_kind, COUNT(*) transactions,
           COALESCE(SUM(quantity), 0) item_quantity, COALESCE(SUM(gross_mesos), 0) meso_volume
    FROM economic_transaction
    WHERE run_id = :run_id AND logical_at BETWEEN :from_logical_at AND :to_logical_at
    GROUP BY transaction_kind
), item_burn AS (
    SELECT item_id, SUM(consumed_quantity) consumed_quantity,
           SUM(npc_created_quantity + farm_created_quantity + quest_created_quantity
               + transformed_created_quantity) created_quantity,
           SUM(npc_destroyed_quantity) npc_destroyed_quantity
    FROM item_market_daily
    WHERE run_id = :run_id AND logical_date BETWEEN :from_logical_at::date AND :to_logical_at::date
    GROUP BY item_id
    HAVING SUM(consumed_quantity + npc_destroyed_quantity) > 0
), item_prices AS (
    SELECT logical_date, item_id, vwap, completed_quantity, completed_trade_count, meso_volume
    FROM item_market_daily
    WHERE run_id = :run_id AND logical_date BETWEEN :from_logical_at::date AND :to_logical_at::date
      AND completed_trade_count > 0
), decision_failures AS (
    SELECT COUNT(*) FILTER (WHERE decision_kind = 'MARKET_TRIP_BLOCKED') blocked_market_trips,
           COUNT(*) FILTER (WHERE decision_kind = 'OBSERVED_PURCHASE'
               AND chosen_action ->> 'action' = 'PASS') observed_purchase_passes
    FROM decision_journal
    WHERE run_id = :run_id AND logical_time BETWEEN :from_logical_at AND :to_logical_at
), demand AS (
    SELECT COUNT(*) FILTER (WHERE status = 'OPEN') open_demands,
           COALESCE(SUM(required_quantity) FILTER (WHERE status = 'OPEN'), 0) open_units
    FROM agent_demand WHERE run_id = :run_id AND earliest_at <= :to_logical_at
), invariant_counts AS (
    SELECT COUNT(*) total, COUNT(*) FILTER (WHERE resolved_at IS NULL) unresolved
    FROM economy_invariant_violation WHERE run_id = :run_id
)
SELECT jsonb_build_object(
    'logicalWindow', jsonb_build_object('from', :from_logical_at, 'to', :to_logical_at),
    'moneySupply', jsonb_build_object(
        'walletMeso', COALESCE((SELECT SUM(balance) FROM money_locations
                                WHERE account_type IN ('AGENT','HUMAN')), 0),
        'escrowMeso', COALESCE((SELECT SUM(balance) FROM money_locations
                                WHERE account_type = 'ESCROW'), 0),
        'marketTransferVolume', (SELECT volume FROM transfer_volume),
        'endingSupplyVelocity', COALESCE((SELECT volume FROM transfer_volume)
            / NULLIF((SELECT SUM(balance) FROM money_locations), 0), 0)),
    'wealth', (SELECT to_jsonb(wealth) FROM wealth),
    'sellerConcentration', (SELECT to_jsonb(seller_concentration) FROM seller_concentration),
    'stalls', jsonb_build_object(
        'opened', (SELECT stalls_opened FROM stall_time),
        'occupiedRoomSeconds', (SELECT occupied_room_seconds FROM stall_time),
        'roomCapacityUtilization', COALESCE((SELECT occupied_room_seconds /
            NULLIF(22 * EXTRACT(EPOCH FROM (:to_logical_at - :from_logical_at)), 0)
            FROM stall_time), 0)),
    'roomTraffic', COALESCE((SELECT jsonb_agg(to_jsonb(room_traffic) ORDER BY map_id)
                             FROM room_traffic), '[]'),
    'npcAndMarketDisposition', COALESCE((SELECT jsonb_agg(to_jsonb(disposition)
                                      ORDER BY transaction_kind) FROM disposition), '[]'),
    'itemCreationAndBurn', COALESCE((SELECT jsonb_agg(to_jsonb(item_burn)
                                  ORDER BY consumed_quantity DESC, item_id) FROM item_burn), '[]'),
    'itemPriceSeries', COALESCE((SELECT jsonb_agg(to_jsonb(item_prices)
                              ORDER BY logical_date, item_id) FROM item_prices), '[]'),
    'searchOutcomes', (SELECT to_jsonb(decision_failures) FROM decision_failures),
    'unmetDemand', (SELECT to_jsonb(demand) FROM demand),
    'invariants', (SELECT to_jsonb(invariant_counts) FROM invariant_counts)
) AS macro_dashboard;
