-- Parameters: :run_id, :base_logical_date, :from_logical_date, :to_logical_date, :basket_json
-- basket_json example: '[{"item_id":2000000,"quantity":100},{"item_id":2070000,"quantity":1}]'
-- Only completed-sale VWAPs are used. Missing prices are exposed as incomplete coverage.
WITH basket AS (
    SELECT item_id, quantity::numeric
    FROM jsonb_to_recordset(CAST(:basket_json AS jsonb))
         AS value(item_id integer, quantity numeric)
    WHERE item_id > 0 AND quantity > 0
), days AS (
    SELECT generate_series(:from_logical_date::date, :to_logical_date::date,
                           interval '1 day')::date AS logical_date
), marks AS (
    SELECT d.logical_date, b.item_id, b.quantity,
           (SELECT m.vwap FROM item_market_daily m
            WHERE m.run_id = :run_id AND m.item_id = b.item_id
              AND m.logical_date <= d.logical_date
              AND m.completed_trade_count > 0 AND m.vwap IS NOT NULL
            ORDER BY m.logical_date DESC LIMIT 1) clearing_vwap
    FROM days d CROSS JOIN basket b
), values AS (
    SELECT logical_date, COUNT(*) basket_items,
           COUNT(clearing_vwap) priced_items,
           SUM(quantity * clearing_vwap) FILTER (WHERE clearing_vwap IS NOT NULL) partial_value,
           CASE WHEN COUNT(clearing_vwap) = COUNT(*)
                THEN SUM(quantity * clearing_vwap) END complete_value
    FROM marks GROUP BY logical_date
), base AS (
    SELECT complete_value FROM values WHERE logical_date = :base_logical_date::date
)
SELECT v.logical_date, v.basket_items, v.priced_items,
       v.priced_items::numeric / NULLIF(v.basket_items, 0) coverage,
       v.partial_value, v.complete_value,
       CASE WHEN v.complete_value IS NOT NULL AND b.complete_value > 0
            THEN 100 * v.complete_value / b.complete_value END fixed_basket_index
FROM values v CROSS JOIN base b
ORDER BY v.logical_date;
