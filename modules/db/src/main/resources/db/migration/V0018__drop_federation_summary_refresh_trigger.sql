-- Move materialized view refresh to application layer to avoid
-- blocking the cache-update transaction for 10+ seconds.
DROP TRIGGER IF EXISTS refresh_federation_summary_trigger ON cache;
DROP FUNCTION IF EXISTS refresh_federations_summary();
