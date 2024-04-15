CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX players_name_gin_idx on players using GIN(name gin_trgm_ops);
CREATE INDEX federations_gin_idx on federations using GIN(name gin_trgm_ops);
