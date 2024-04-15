ALTER TABLE IF EXISTS players
DROP COLUMN IF EXISTS other_title;

ALTER TABLE IF EXISTS players
Add column if not exists other_titles text default null;
