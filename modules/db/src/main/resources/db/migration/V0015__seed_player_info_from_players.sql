-- Seed player_info from existing players table
INSERT INTO player_info (id, name, sex, birth_year, created_at, updated_at)
SELECT id, name, sex, birth_year, created_at, updated_at
FROM players
ON CONFLICT (id) DO NOTHING;
