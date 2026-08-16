ALTER TABLE private_trade_arrangement
    ADD COLUMN resolution_reason TEXT;

CREATE INDEX private_trade_arrangement_pending_buyer_idx
    ON private_trade_arrangement(run_id, buyer_id, created_at)
    WHERE status = 'PENDING_MEETUP';
