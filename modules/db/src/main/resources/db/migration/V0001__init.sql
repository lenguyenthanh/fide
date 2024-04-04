CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TYPE title AS ENUM ('GM', 'WGM', 'IM', 'WIM', 'FM', 'WFM', 'CM', 'WCM', 'NM', 'WNM');

CREATE TABLE IF NOT EXISTS players
(
    id                 integer PRIMARY KEY, -- fide id
    name               text NOT NULL,
    title              title,
    standard           integer,
    rapid              integer,
    blitz              integer,
    year               integer,
    active             boolean,
    created_at         timestamptz NOT NULL DEFAULT NOW(),
    updated_at         timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS federations
(
    id                 text PRIMARY KEY,
    name               text NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT NOW(),
    updated_at         timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS player_federation
(
    player_id          integer NOT NULL,
    federation_id      text NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT NOW(),
    updated_at         timestamptz NOT NULL DEFAULT NOW(),
    FOREIGN KEY (player_id) REFERENCES players,
    FOREIGN KEY (federation_id) REFERENCES federations,
    PRIMARY KEY (player_id, federation_id)
);


CREATE TRIGGER set_players_updated_at
BEFORE UPDATE ON players
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE TRIGGER set_federations_updated_at
BEFORE UPDATE ON federations
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE TRIGGER set_player_federation_updated_at
BEFORE UPDATE ON player_federation
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();
