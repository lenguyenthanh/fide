-- Add hash column for incremental crawl diffing.
-- Default 0 means "unknown hash" — all existing rows will be re-emitted on first crawl.
ALTER TABLE players ADD COLUMN hash BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN last_seen_at TIMESTAMPTZ;
