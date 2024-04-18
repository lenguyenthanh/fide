ALTER TABLE IF EXISTS players
DROP COLUMN IF EXISTS other_titles;

ALTER TABLE IF EXISTS players
ADD COLUMN IF NOT EXISTS other_titles other_title [] default null;
