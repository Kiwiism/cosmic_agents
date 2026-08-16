ALTER TABLE item_market_daily
    ADD COLUMN quest_created_quantity BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN transformed_created_quantity BIGINT NOT NULL DEFAULT 0;
