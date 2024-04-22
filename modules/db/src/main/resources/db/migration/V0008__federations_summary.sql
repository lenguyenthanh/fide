-- function to calculate average of 3 values (use for calculating average of standard, rapid, blitz rating)
CREATE OR REPLACE FUNCTION AVERAGE (
V1 NUMERIC,
V2 NUMERIC,
V3 NUMERIC)
RETURNS NUMERIC
AS $FUNCTION$
DECLARE
    COUNT NUMERIC;
    TOTAL NUMERIC;
BEGIN
    COUNT=0;
    TOTAL=0;
    IF V1 IS NOT NULL THEN COUNT=COUNT+1; TOTAL=TOTAL+V1; END IF;
    IF V2 IS NOT NULL THEN COUNT=COUNT+1; TOTAL=TOTAL+V2; END IF;
    IF V3 IS NOT NULL THEN COUNT=COUNT+1; TOTAL=TOTAL+V3; END IF;
    RETURN TOTAL/COUNT;
    EXCEPTION WHEN DIVISION_BY_ZERO THEN RETURN NULL;
END
$FUNCTION$ LANGUAGE PLPGSQL;

-- view federation summary with total players and average rating of each time control
CREATE OR REPLACE VIEW federations_with_players_count_and_avg_rating AS
(
  WITH f1 AS (
    SELECT federations.id, federations.name, COUNT(players.id) as players, ROUND(avg(players.standard)) as avg_standard, ROUND(avg(players.rapid)) as avg_rapid, ROUND(avg(players.blitz)) as avg_blitz, COUNT(COALESCE(players.standard, NULL)) as standard_players, COUNT(COALESCE(players.rapid, NULL)) as rapid_players, COUNT(COALESCE(players.blitz, NULL)) as blitz_players
    FROM players, federations
    WHERE players.federation_id = federations.id AND players.active = true
    GROUP BY federations.id)
  SELECT *, ROUND(average(f1.avg_standard, f1.avg_rapid, f1.avg_blitz)) as avg_rating
  FROM f1
);

-- average top 10 rating with ranking
CREATE OR REPLACE VIEW federations_avg_top_10_ranking AS (

-- ranking players for each time control
with fed_with_ranking AS
(SELECT federations.id, players.standard, players.rapid, players.blitz,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY players.standard DESC NULLS LAST
        ) as standard_rank,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY players.rapid DESC NULLS LAST
        ) as rapid_rank,
rank() OVER (
    PARTITION BY federations.id
    ORDER BY players.blitz DESC NULLS LAST
        ) as blitz_rank
FROM players, federations
WHERE players.federation_id = federations.id AND players.active = true),

-- average top 10 rating
fed_with_avg_top_10 as
(select t1.id, t1.avg_top_standard, t2.avg_top_rapid, t3.avg_top_blitz
FROM
(SELECT fed_with_ranking.id, ROUND(avg(standard)) as avg_top_standard
    FROM fed_with_ranking
    WHERE fed_with_ranking.standard_rank <= 10
    GROUP BY fed_with_ranking.id) as t1
LEFT JOIN
(SELECT fed_with_ranking.id, ROUND(avg(rapid)) as avg_top_rapid
    FROM fed_with_ranking
    WHERE fed_with_ranking.rapid_rank <= 10
    GROUP BY fed_with_ranking.id) as t2
ON t1.id = t2.id
LEFT JOIN
(SELECT fed_with_ranking.id, ROUND(avg(blitz)) as avg_top_blitz
    FROM fed_with_ranking
    WHERE fed_with_ranking.blitz_rank <= 10
    GROUP BY fed_with_ranking.id) as t3
ON t1.id = t3.id),

-- average top 10 rating with ranking
fed_with_avg_top_10_ranking AS (
SELECT fed_with_avg_top_10.id, fed_with_avg_top_10.avg_top_standard, fed_with_avg_top_10.avg_top_rapid, fed_with_avg_top_10.avg_top_blitz,
rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_standard DESC NULLS LAST
        ) as avg_top_standard_rank,
rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_rapid DESC NULLS LAST
        ) as avg_top_rapid_rank,
rank() OVER (
    ORDER BY fed_with_avg_top_10.avg_top_blitz DESC NULLS LAST
        ) as avg_top_blitz_rank
FROM fed_with_avg_top_10)
select * from fed_with_avg_top_10_ranking
);

CREATE MATERIALIZED VIEW  IF NOT EXISTS federations_summary as
select f1.*, f2.avg_top_standard, f2.avg_top_rapid, f2.avg_top_blitz, f2.avg_top_standard_rank, f2.avg_top_rapid_rank, f2.avg_top_blitz_rank
from federations_with_players_count_and_avg_rating as f1, federations_avg_top_10_ranking as f2
where f1.id = f2.id;

create unique index on federations_summary (id);
CREATE INDEX fed_sum_avg_rating_idx ON federations_summary(avg_rating);
CREATE INDEX fed_sum_avg_standard_idx ON federations_summary(avg_standard);
CREATE INDEX fed_sum_avg_rapid_idx ON federations_summary(avg_rapid);
CREATE INDEX fed_sum_avg_blitz_idx ON federations_summary(avg_blitz);
CREATE INDEX fed_sum_avg_top_standard_idx ON federations_summary(avg_top_standard);
CREATE INDEX fed_sum_avg_top_rapid_idx ON federations_summary(avg_top_rapid);
CREATE INDEX fed_sum_avg_top_blitz_idx ON federations_summary(avg_top_blitz);
CREATE INDEX fed_sum_avg_top_standard_rank_idx ON federations_summary(avg_top_standard_rank);
CREATE INDEX fed_sum_avg_top_rapid_rank_idx ON federations_summary(avg_top_rapid_rank);
CREATE INDEX fed_sum_avg_top_blitz_rank_idx ON federations_summary(avg_top_blitz_rank);
CREATE INDEX fed_sum_standard_players_idx ON federations_summary(standard_players);
CREATE INDEX fed_sum_rapid_players_idx ON federations_summary(rapid_players);
CREATE INDEX fed_sum_blitz_players_idx ON federations_summary(blitz_players);
CREATE INDEX fed_sum_players_idx ON federations_summary(players);

-- AFTER INSERT/UPDATE on 'last_updated_key' column of `cache` table
CREATE OR REPLACE FUNCTION refresh_federations_summary()
RETURNS TRIGGER LANGUAGE plpgsql
AS $$
BEGIN
REFRESH MATERIALIZED VIEW CONCURRENTLY federations_summary;
RETURN NULL;
END $$;

CREATE TRIGGER refresh_federation_summary_trigger
AFTER UPDATE OR INSERT
ON cache
FOR EACH ROW
WHEN (NEW.key = 'fide_last_update_key')
EXECUTE PROCEDURE refresh_federations_summary();
