ALTER TABLE IF EXISTS players
ADD COLUMN IF NOT EXISTS standard_kfactor integer default null;

ALTER TABLE IF EXISTS players
ADD COLUMN IF NOT EXISTS rapid_kfactor integer default null;

ALTER TABLE IF EXISTS players
ADD COLUMN IF NOT EXISTS blitz_kfactor integer default null;
