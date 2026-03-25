-- Add hash column for change-only events, drop raw_data
ALTER TABLE player_events ADD COLUMN hash BIGINT NOT NULL DEFAULT 0;
ALTER TABLE player_events DROP COLUMN raw_data;
