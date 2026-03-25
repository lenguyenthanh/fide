-- Auto-purge ingested player_events older than 90 days using a trigger.
-- This replaces the Scala-side purge logic.
CREATE OR REPLACE FUNCTION purge_old_player_events()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM player_events
  WHERE ingested = TRUE AND created_at < now() - interval '90 days';
  RETURN NULL;
END $$;

-- Fire once per INSERT statement (not per row) to avoid excessive deletes
CREATE TRIGGER player_events_auto_purge
AFTER INSERT ON player_events
FOR EACH STATEMENT
EXECUTE FUNCTION purge_old_player_events();
