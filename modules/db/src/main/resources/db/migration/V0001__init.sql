-- Utility function for auto-updating timestamps
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Custom types
CREATE TYPE title AS ENUM ('GM', 'WGM', 'IM', 'WIM', 'FM', 'WFM', 'CM', 'WCM', 'NM', 'WNM');
CREATE TYPE other_title AS ENUM ('IA', 'FA', 'NA', 'IO', 'FT', 'FI', 'FST', 'DI', 'NI', 'SI', 'LSI');
CREATE TYPE sex AS ENUM ('M', 'F');

-- ============================================================
-- Federations
-- ============================================================
CREATE TABLE IF NOT EXISTS federations (
    id          text PRIMARY KEY,
    name        text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT NOW(),
    updated_at  timestamptz NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_federations_updated_at
BEFORE UPDATE ON federations
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE INDEX federations_gin_idx ON federations USING gin(name gin_trgm_ops);

-- ============================================================
-- Player info (authoritative identity table)
-- ============================================================
CREATE TABLE IF NOT EXISTS player_info (
    id          integer PRIMARY KEY,
    fide_id     text UNIQUE,
    name        text NOT NULL,
    sex         sex,
    birth_year  integer,
    hash        bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT NOW(),
    updated_at  timestamptz NOT NULL DEFAULT NOW()
);

CREATE SEQUENCE IF NOT EXISTS player_info_id_seq OWNED BY player_info.id;

CREATE TRIGGER set_player_info_updated_at
BEFORE UPDATE ON player_info
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE INDEX player_info_name_gin_idx ON player_info USING gin(name gin_trgm_ops);

-- ============================================================
-- Players (current/live ratings from FIDE crawl)
-- ============================================================
CREATE TABLE IF NOT EXISTS players (
    id                  integer PRIMARY KEY REFERENCES player_info(id),
    fide_id             text UNIQUE,
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
    federation_id       text REFERENCES federations(id),
    hash                bigint NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT NOW(),
    updated_at          timestamptz NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_players_updated_at
BEFORE UPDATE ON players
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE INDEX players_name_idx ON players(name);
CREATE INDEX players_name_desc_idx ON players(name DESC NULLS LAST);
CREATE INDEX players_name_gin_idx ON players USING gin(name gin_trgm_ops);
CREATE INDEX players_federation_id_idx ON players(federation_id);
CREATE INDEX players_standard_active_idx ON players(standard DESC NULLS LAST) WHERE active = true;
CREATE INDEX players_rapid_active_idx ON players(rapid DESC NULLS LAST) WHERE active = true;
CREATE INDEX players_blitz_active_idx ON players(blitz DESC NULLS LAST) WHERE active = true;
CREATE INDEX players_federation_id_active_idx ON players(federation_id) WHERE active = true;

-- ============================================================
-- Player history (monthly rating snapshots)
-- ============================================================
CREATE TABLE IF NOT EXISTS player_history (
    player_id           integer NOT NULL REFERENCES player_info(id),
    fide_id             text,
    year_month          date NOT NULL,
    title               title,
    women_title         title,
    other_titles        other_title[] DEFAULT NULL,
    standard            integer,
    standard_kfactor    integer,
    rapid               integer,
    rapid_kfactor       integer,
    blitz               integer,
    blitz_kfactor       integer,
    federation_id       text REFERENCES federations(id),
    active              boolean NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT NOW(),
    updated_at          timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, year_month)
);

CREATE TRIGGER set_player_history_updated_at
BEFORE UPDATE ON player_history
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

CREATE INDEX player_history_year_month_idx ON player_history(year_month);
CREATE INDEX player_history_federation_id_year_month_idx ON player_history(year_month, federation_id);
CREATE INDEX player_history_standard_idx ON player_history(year_month, standard);
CREATE INDEX player_history_rapid_idx ON player_history(year_month, rapid);
CREATE INDEX player_history_blitz_idx ON player_history(year_month, blitz);
CREATE INDEX player_history_active_idx ON player_history(year_month, active);
CREATE INDEX player_history_fide_id_idx ON player_history(fide_id) WHERE fide_id IS NOT NULL;

-- ============================================================
-- Player events (raw crawl change records, staging queue)
-- ============================================================
CREATE TABLE IF NOT EXISTS player_events (
    id                  bigserial PRIMARY KEY,
    fide_id             text,
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
    hash                bigint NOT NULL DEFAULT 0,
    crawled_at          timestamptz NOT NULL,
    source_last_modified text,
    ingested            boolean NOT NULL DEFAULT FALSE,
    created_at          timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX player_events_uningested_idx ON player_events(ingested) WHERE ingested = FALSE;
CREATE INDEX player_events_created_at_idx ON player_events(created_at);

-- ============================================================
-- Cache (key-value metadata store)
-- ============================================================
CREATE TABLE IF NOT EXISTS cache (
    id      serial PRIMARY KEY,
    key     text UNIQUE NOT NULL,
    value   text NOT NULL
);

-- ============================================================
-- Federations summary (materialized view)
-- ============================================================
CREATE OR REPLACE FUNCTION AVERAGE(V1 NUMERIC, V2 NUMERIC, V3 NUMERIC)
RETURNS NUMERIC AS $FUNCTION$
DECLARE
    CNT NUMERIC;
    TOTAL NUMERIC;
BEGIN
    CNT=0;
    TOTAL=0;
    IF V1 IS NOT NULL THEN CNT=CNT+1; TOTAL=TOTAL+V1; END IF;
    IF V2 IS NOT NULL THEN CNT=CNT+1; TOTAL=TOTAL+V2; END IF;
    IF V3 IS NOT NULL THEN CNT=CNT+1; TOTAL=TOTAL+V3; END IF;
    RETURN CASE WHEN CNT = 0 THEN NULL ELSE TOTAL/CNT END;
END
$FUNCTION$ LANGUAGE PLPGSQL;

CREATE OR REPLACE VIEW federations_with_players_count_and_avg_rating AS (
  WITH f1 AS (
    SELECT f.id, f.name,
      CAST(COUNT(p.id) AS integer) as players,
      CAST(ROUND(avg(p.standard)) as integer) as avg_standard,
      CAST(ROUND(avg(p.rapid)) as integer) as avg_rapid,
      CAST(ROUND(avg(p.blitz)) as integer) as avg_blitz,
      CAST(COUNT(p.standard) as integer) as standard_players,
      CAST(COUNT(p.rapid) as integer) as rapid_players,
      CAST(COUNT(p.blitz) as integer) as blitz_players
    FROM federations AS f
    JOIN players AS p ON p.federation_id = f.id
    WHERE p.active = true
    GROUP BY f.id)
  SELECT *, ROUND(average(f1.avg_standard, f1.avg_rapid, f1.avg_blitz)) as avg_rating
  FROM f1
);

CREATE OR REPLACE VIEW federations_avg_top_10_ranking AS (
  WITH fed_with_ranking AS (
    SELECT f.id, p.standard, p.rapid, p.blitz,
      row_number() OVER (PARTITION BY f.id ORDER BY p.standard DESC NULLS LAST) as standard_rank,
      row_number() OVER (PARTITION BY f.id ORDER BY p.rapid DESC NULLS LAST) as rapid_rank,
      row_number() OVER (PARTITION BY f.id ORDER BY p.blitz DESC NULLS LAST) as blitz_rank
    FROM federations AS f
    JOIN players AS p ON p.federation_id = f.id
    WHERE p.active = true
  ),
  fed_with_avg_top_10 AS (
    SELECT t1.id, t1.avg_top_standard, t2.avg_top_rapid, t3.avg_top_blitz
    FROM
      (SELECT id, CAST(ROUND(avg(standard)) as integer) as avg_top_standard
       FROM fed_with_ranking WHERE standard_rank <= 10 GROUP BY id) as t1
    LEFT JOIN
      (SELECT id, CAST(ROUND(avg(rapid)) as integer) as avg_top_rapid
       FROM fed_with_ranking WHERE rapid_rank <= 10 GROUP BY id) as t2 ON t1.id = t2.id
    LEFT JOIN
      (SELECT id, CAST(ROUND(avg(blitz)) as integer) as avg_top_blitz
       FROM fed_with_ranking WHERE blitz_rank <= 10 GROUP BY id) as t3 ON t1.id = t3.id
  ),
  fed_with_avg_top_10_ranking AS (
    SELECT *,
      CAST(rank() OVER (ORDER BY avg_top_standard DESC NULLS LAST) as integer) as avg_top_standard_rank,
      CAST(rank() OVER (ORDER BY avg_top_rapid DESC NULLS LAST) as integer) as avg_top_rapid_rank,
      CAST(rank() OVER (ORDER BY avg_top_blitz DESC NULLS LAST) as integer) as avg_top_blitz_rank
    FROM fed_with_avg_top_10
  )
  SELECT * FROM fed_with_avg_top_10_ranking
);

CREATE MATERIALIZED VIEW IF NOT EXISTS federations_summary AS
  SELECT f1.*, f2.avg_top_standard, f2.avg_top_rapid, f2.avg_top_blitz,
    f2.avg_top_standard_rank, f2.avg_top_rapid_rank, f2.avg_top_blitz_rank
  FROM federations_with_players_count_and_avg_rating AS f1
  JOIN federations_avg_top_10_ranking AS f2 ON f1.id = f2.id;

CREATE UNIQUE INDEX ON federations_summary (id);
