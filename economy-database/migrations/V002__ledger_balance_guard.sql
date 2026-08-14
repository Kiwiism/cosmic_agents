CREATE OR REPLACE FUNCTION verify_event_postings_balance() RETURNS trigger AS $$
DECLARE
    bad_asset TEXT;
BEGIN
    SELECT asset_type || ':' || asset_identifier
      INTO bad_asset
      FROM ledger_posting
     WHERE event_id = COALESCE(NEW.event_id, OLD.event_id)
     GROUP BY asset_type, asset_identifier
    HAVING SUM(quantity) <> 0
     LIMIT 1;
    IF bad_asset IS NOT NULL THEN
        RAISE EXCEPTION 'unbalanced economic event %, asset %',
            COALESCE(NEW.event_id, OLD.event_id), bad_asset;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_postings_must_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_posting
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION verify_event_postings_balance();
