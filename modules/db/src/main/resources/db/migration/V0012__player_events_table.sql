CREATE TABLE IF NOT EXISTS player_events (
    id                  BIGSERIAL PRIMARY KEY,
    player_id           integer NOT NULL,
    name                text NOT NULL,
    title               title,
    women_title         title,
    other_titles        other_title[] DEFAULT NULL,
    standard            integer,
    standard_kfactor    integer,
    rapid               integer,
    rapid_kfactor       integer,
    blitz               integer,
    blitz_kfactor       integer,
    sex                 sex,
    birth_year          integer,
    active              boolean NOT NULL,
    federation_id       text,
    raw_data            text NOT NULL,
    crawled_at          timestamptz NOT NULL,
    source_last_modified text,
    ingested            boolean NOT NULL DEFAULT FALSE,
    created_at          timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX player_events_uningested_idx ON player_events(ingested) WHERE ingested = FALSE;
CREATE INDEX player_events_created_at_idx ON player_events(created_at);
