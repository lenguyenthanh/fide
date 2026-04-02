CREATE TABLE IF NOT EXISTS player_info (
    id              integer PRIMARY KEY,
    name            text NOT NULL,
    sex             sex,
    birth_year      integer,
    hash            BIGINT NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT NOW(),
    updated_at      timestamptz NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_player_info_updated_at
BEFORE UPDATE ON player_info
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE TABLE IF NOT EXISTS player_history (
    player_id       integer NOT NULL REFERENCES player_info(id),
    year_month      date NOT NULL,
    title           title,
    women_title     title,
    other_titles    other_title[] DEFAULT NULL,
    standard        integer,
    standard_kfactor integer,
    rapid           integer,
    rapid_kfactor   integer,
    blitz           integer,
    blitz_kfactor   integer,
    federation_id   text REFERENCES federations(id),
    active          boolean NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT NOW(),
    updated_at      timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, year_month)
);

CREATE INDEX player_history_year_month_idx ON player_history(year_month);
CREATE INDEX player_history_federation_id_year_month_idx ON player_history(year_month, federation_id);
CREATE INDEX player_history_standard_idx ON player_history(year_month, standard);
CREATE INDEX player_history_rapid_idx ON player_history(year_month, rapid);
CREATE INDEX player_history_blitz_idx ON player_history(year_month, blitz);
CREATE INDEX player_history_active_idx ON player_history(year_month, active);

CREATE INDEX player_info_name_gin_idx ON player_info USING gin(name gin_trgm_ops);

CREATE TRIGGER set_player_history_updated_at
BEFORE UPDATE ON player_history
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

-- Seed player_info from existing players table
INSERT INTO player_info (id, name, sex, birth_year, created_at, updated_at)
SELECT id, name, sex, birth_year, created_at, updated_at
FROM players
ON CONFLICT (id) DO NOTHING;
