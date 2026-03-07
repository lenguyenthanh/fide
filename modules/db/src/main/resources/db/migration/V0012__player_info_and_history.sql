-- Phase 1: Create player_info and player_history tables, migrate data, drop players table.
-- See REFACTOR_PLAN.md for full context.

------------------------------------------------------------
-- Step 1: Create player_info (static biographical data)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS player_info
(
    id                 integer PRIMARY KEY,
    name               text NOT NULL,
    sex                sex,
    birth_year         integer,
    created_at         timestamptz NOT NULL DEFAULT NOW(),
    updated_at         timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX player_info_name_gin_idx ON player_info USING GIN(name gin_trgm_ops);
CREATE INDEX player_info_name_desc_idx ON player_info(name DESC NULLS LAST);

CREATE TRIGGER set_player_info_updated_at
BEFORE UPDATE ON player_info
FOR EACH ROW
EXECUTE PROCEDURE set_updated_at();

------------------------------------------------------------
-- Step 2: Create player_history (monthly snapshots)
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS player_history
(
    player_id          integer NOT NULL,
    month              smallint NOT NULL, -- (year - 1970) * 12 + (month_of_year - 1)
    title              title,
    women_title        title,
    other_titles       other_title[],
    federation_id      text,
    active             boolean NOT NULL,
    standard           integer,
    standard_kfactor   integer,
    rapid              integer,
    rapid_kfactor      integer,
    blitz              integer,
    blitz_kfactor      integer,
    created_at         timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, month),
    FOREIGN KEY (player_id) REFERENCES player_info(id),
    FOREIGN KEY (federation_id) REFERENCES federations(id)
);

CREATE INDEX player_history_month_idx ON player_history(month);
CREATE INDEX player_history_standard_desc_idx ON player_history(standard DESC NULLS LAST);
CREATE INDEX player_history_rapid_desc_idx ON player_history(rapid DESC NULLS LAST);
CREATE INDEX player_history_blitz_desc_idx ON player_history(blitz DESC NULLS LAST);

------------------------------------------------------------
-- Step 3: Migrate data from players -> player_info + player_history
------------------------------------------------------------
INSERT INTO player_info (id, name, sex, birth_year, created_at, updated_at)
SELECT id, name, sex, birth_year, created_at, updated_at
FROM players;

INSERT INTO player_history (player_id, month, title, women_title, other_titles,
    federation_id, active, standard, standard_kfactor, rapid, rapid_kfactor,
    blitz, blitz_kfactor)
SELECT id,
    ((EXTRACT(YEAR FROM NOW())::int - 1970) * 12 + EXTRACT(MONTH FROM NOW())::int - 1)::smallint,
    title, women_title, other_titles,
    federation_id, active, standard, standard_kfactor, rapid, rapid_kfactor,
    blitz, blitz_kfactor
FROM players;

------------------------------------------------------------
-- Step 4: Drop federation views (reverse dependency order)
------------------------------------------------------------
DROP TRIGGER IF EXISTS refresh_federation_summary_trigger ON cache;
DROP MATERIALIZED VIEW IF EXISTS federations_summary;
DROP VIEW IF EXISTS federations_avg_top_10_ranking;
DROP VIEW IF EXISTS federations_with_players_count_and_avg_rating;

------------------------------------------------------------
-- Step 5: Drop old players table (cascades indexes, triggers)
------------------------------------------------------------
DROP TABLE IF EXISTS players;

------------------------------------------------------------
-- Step 6: Create player_current view
------------------------------------------------------------
CREATE VIEW player_current AS
SELECT DISTINCT ON (player_id)
    player_id, month, title, women_title, other_titles, federation_id, active,
    standard, standard_kfactor, rapid, rapid_kfactor, blitz, blitz_kfactor
FROM player_history
ORDER BY player_id, month DESC;

------------------------------------------------------------
-- Step 7: Recreate federation views using player_current
------------------------------------------------------------

-- View: federation summary with total players and average rating
CREATE OR REPLACE VIEW federations_with_players_count_and_avg_rating AS
(
  WITH f1 AS (
    SELECT federations.id, federations.name,
        CAST(COUNT(pc.player_id) AS integer) as players,
        CAST(ROUND(avg(pc.standard)) as integer) as avg_standard,
        CAST(ROUND(avg(pc.rapid)) as integer) as avg_rapid,
        CAST(ROUND(avg(pc.blitz)) as integer) as avg_blitz,
        CAST(COUNT(COALESCE(pc.standard, NULL)) as integer) as standard_players,
        CAST(COUNT(COALESCE(pc.rapid, NULL)) as integer) as rapid_players,
        CAST(COUNT(COALESCE(pc.blitz, NULL)) as integer) as blitz_players
    FROM player_current AS pc, federations
    WHERE pc.federation_id = federations.id AND pc.active = true
    GROUP BY federations.id)
  SELECT *, ROUND(average(f1.avg_standard, f1.avg_rapid, f1.avg_blitz)) as avg_rating
  FROM f1
);

-- View: average top 10 rating with ranking
CREATE OR REPLACE VIEW federations_avg_top_10_ranking AS (

with fed_with_ranking AS
(SELECT federations.id, pc.standard, pc.rapid, pc.blitz,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY pc.standard DESC NULLS LAST
        ) as standard_rank,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY pc.rapid DESC NULLS LAST
        ) as rapid_rank,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY pc.blitz DESC NULLS LAST
        ) as blitz_rank
FROM player_current AS pc, federations
WHERE pc.federation_id = federations.id AND pc.active = true),

fed_with_avg_top_10 as
(select t1.id, t1.avg_top_standard, t2.avg_top_rapid, t3.avg_top_blitz
FROM
(SELECT fed_with_ranking.id, CAST(ROUND(avg(standard)) as integer) as avg_top_standard
    FROM fed_with_ranking
    WHERE fed_with_ranking.standard_rank <= 10
    GROUP BY fed_with_ranking.id) as t1
LEFT JOIN
(SELECT fed_with_ranking.id, CAST(ROUND(avg(rapid)) as integer) as avg_top_rapid
    FROM fed_with_ranking
    WHERE fed_with_ranking.rapid_rank <= 10
    GROUP BY fed_with_ranking.id) as t2
ON t1.id = t2.id
LEFT JOIN
(SELECT fed_with_ranking.id, CAST(ROUND(avg(blitz)) as integer) as avg_top_blitz
    FROM fed_with_ranking
    WHERE fed_with_ranking.blitz_rank <= 10
    GROUP BY fed_with_ranking.id) as t3
ON t1.id = t3.id),

fed_with_avg_top_10_ranking AS (
SELECT fed_with_avg_top_10.id, fed_with_avg_top_10.avg_top_standard, fed_with_avg_top_10.avg_top_rapid, fed_with_avg_top_10.avg_top_blitz,
CAST(rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_standard DESC NULLS LAST
        ) as integer) as avg_top_standard_rank,
CAST(rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_rapid DESC NULLS LAST
        ) as integer) as avg_top_rapid_rank,
CAST(rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_blitz DESC NULLS LAST
        ) as integer) as avg_top_blitz_rank
FROM fed_with_avg_top_10)
select * from fed_with_avg_top_10_ranking
);

-- Materialized view: combined federation summary
CREATE MATERIALIZED VIEW IF NOT EXISTS federations_summary AS
SELECT f1.*, f2.avg_top_standard, f2.avg_top_rapid, f2.avg_top_blitz, f2.avg_top_standard_rank, f2.avg_top_rapid_rank, f2.avg_top_blitz_rank
FROM federations_with_players_count_and_avg_rating AS f1, federations_avg_top_10_ranking AS f2
WHERE f1.id = f2.id;

CREATE UNIQUE INDEX ON federations_summary (id);
CREATE INDEX fed_sum_avg_rating_idx_v2 ON federations_summary(avg_rating);
CREATE INDEX fed_sum_avg_standard_idx_v2 ON federations_summary(avg_standard);
CREATE INDEX fed_sum_avg_rapid_idx_v2 ON federations_summary(avg_rapid);
CREATE INDEX fed_sum_avg_blitz_idx_v2 ON federations_summary(avg_blitz);
CREATE INDEX fed_sum_avg_top_standard_idx_v2 ON federations_summary(avg_top_standard);
CREATE INDEX fed_sum_avg_top_rapid_idx_v2 ON federations_summary(avg_top_rapid);
CREATE INDEX fed_sum_avg_top_blitz_idx_v2 ON federations_summary(avg_top_blitz);
CREATE INDEX fed_sum_avg_top_standard_rank_idx_v2 ON federations_summary(avg_top_standard_rank);
CREATE INDEX fed_sum_avg_top_rapid_rank_idx_v2 ON federations_summary(avg_top_rapid_rank);
CREATE INDEX fed_sum_avg_top_blitz_rank_idx_v2 ON federations_summary(avg_top_blitz_rank);
CREATE INDEX fed_sum_standard_players_idx_v2 ON federations_summary(standard_players);
CREATE INDEX fed_sum_rapid_players_idx_v2 ON federations_summary(rapid_players);
CREATE INDEX fed_sum_blitz_players_idx_v2 ON federations_summary(blitz_players);
CREATE INDEX fed_sum_players_idx_v2 ON federations_summary(players);
-- DESC indexes
CREATE INDEX fed_sum_avg_rating_desc_idx_v2 ON federations_summary(avg_rating DESC NULLS LAST);
CREATE INDEX fed_sum_avg_standard_desc_idx_v2 ON federations_summary(avg_standard DESC NULLS LAST);
CREATE INDEX fed_sum_avg_rapid_desc_idx_v2 ON federations_summary(avg_rapid DESC NULLS LAST);
CREATE INDEX fed_sum_avg_blitz_desc_idx_v2 ON federations_summary(avg_blitz DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_standard_desc_idx_v2 ON federations_summary(avg_top_standard DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_rapid_desc_idx_v2 ON federations_summary(avg_top_rapid DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_blitz_desc_idx_v2 ON federations_summary(avg_top_blitz DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_standard_rank_desc_idx_v2 ON federations_summary(avg_top_standard_rank DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_rapid_rank_desc_idx_v2 ON federations_summary(avg_top_rapid_rank DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_blitz_rank_desc_idx_v2 ON federations_summary(avg_top_blitz_rank DESC NULLS LAST);
CREATE INDEX fed_sum_standard_players_desc_idx_v2 ON federations_summary(standard_players DESC NULLS LAST);
CREATE INDEX fed_sum_rapid_players_desc_idx_v2 ON federations_summary(rapid_players DESC NULLS LAST);
CREATE INDEX fed_sum_blitz_players_desc_idx_v2 ON federations_summary(blitz_players DESC NULLS LAST);
CREATE INDEX fed_sum_players_desc_idx_v2 ON federations_summary(players DESC NULLS LAST);

------------------------------------------------------------
-- Step 8: Recreate federation summary refresh trigger
------------------------------------------------------------
CREATE TRIGGER refresh_federation_summary_trigger
AFTER UPDATE OR INSERT
ON cache
FOR EACH ROW
WHEN (NEW.key = 'fide_last_update_key')
EXECUTE PROCEDURE refresh_federations_summary();
