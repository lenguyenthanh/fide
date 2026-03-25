CREATE TABLE IF NOT EXISTS player_info (
    id              integer PRIMARY KEY,
    name            text NOT NULL,
    sex             sex,
    birth_year      integer,
    created_at      timestamptz NOT NULL DEFAULT NOW(),
    updated_at      timestamptz NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_player_info_updated_at
BEFORE UPDATE ON player_info
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();
