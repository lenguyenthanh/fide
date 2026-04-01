-- Drop plain ASC rating indexes: redundant because Postgres can scan DESC indexes
-- backward for ASC queries, and partial indexes below cover the hot path.
DROP INDEX IF EXISTS players_standard_idx;
DROP INDEX IF EXISTS players_rapid_idx;
DROP INDEX IF EXISTS players_blitz_idx;

-- Partial indexes scoped to active players.
-- All API queries filter WHERE active = true; the planner will prefer these
-- smaller, denser indexes over the full players_*_desc_idx alternatives.
CREATE INDEX players_standard_active_idx ON players (standard DESC NULLS LAST) WHERE active = true;
CREATE INDEX players_rapid_active_idx    ON players (rapid    DESC NULLS LAST) WHERE active = true;
CREATE INDEX players_blitz_active_idx    ON players (blitz    DESC NULLS LAST) WHERE active = true;

-- Partial index for federation-scoped lookups (replaces players_federation_id_idx for
-- the active-player query path; the full index is kept for crawler/admin queries).
CREATE INDEX players_federation_id_active_idx ON players (federation_id) WHERE active = true;
