-- Move purge logic to application layer (once per ingest cycle)
-- instead of firing on every INSERT statement.
DROP TRIGGER IF EXISTS player_events_auto_purge ON player_events;
DROP FUNCTION IF EXISTS purge_old_player_events();
