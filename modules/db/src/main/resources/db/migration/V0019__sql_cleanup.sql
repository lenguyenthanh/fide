-- Drop redundant cache_key_idx (cache.key already has UNIQUE constraint)
DROP INDEX IF EXISTS cache_key_idx;

-- Replace EXCEPTION-based div-by-zero with CASE WHEN
CREATE OR REPLACE FUNCTION AVERAGE (
V1 NUMERIC,
V2 NUMERIC,
V3 NUMERIC)
RETURNS NUMERIC
AS $FUNCTION$
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

-- Replace implicit cross-joins with explicit JOINs in federation views
CREATE OR REPLACE VIEW federations_with_players_count_and_avg_rating AS
(
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

with fed_with_ranking AS
(SELECT f.id, p.standard, p.rapid, p.blitz,
row_number() OVER (
    PARTITION BY f.id
    ORDER BY p.standard DESC NULLS LAST
        ) as standard_rank,
row_number() OVER (
    PARTITION BY f.id
    ORDER BY p.rapid DESC NULLS LAST
        ) as rapid_rank,
row_number() OVER (
    PARTITION BY f.id
    ORDER BY p.blitz DESC NULLS LAST
        ) as blitz_rank
FROM federations AS f
JOIN players AS p ON p.federation_id = f.id
WHERE p.active = true),

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

-- Drop redundant indexes on federations_summary (~200 rows, seq scan always wins)
-- Keep only the unique index on (id) required for REFRESH CONCURRENTLY
DROP INDEX IF EXISTS fed_sum_avg_rating_idx;
DROP INDEX IF EXISTS fed_sum_avg_standard_idx;
DROP INDEX IF EXISTS fed_sum_avg_rapid_idx;
DROP INDEX IF EXISTS fed_sum_avg_blitz_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_standard_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_rapid_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_blitz_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_standard_rank_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_rapid_rank_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_blitz_rank_idx;
DROP INDEX IF EXISTS fed_sum_standard_players_idx;
DROP INDEX IF EXISTS fed_sum_rapid_players_idx;
DROP INDEX IF EXISTS fed_sum_blitz_players_idx;
DROP INDEX IF EXISTS fed_sum_players_idx;
DROP INDEX IF EXISTS fed_sum_avg_rating_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_standard_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_rapid_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_blitz_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_standard_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_rapid_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_blitz_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_standard_rank_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_rapid_rank_desc_idx;
DROP INDEX IF EXISTS fed_sum_avg_top_blitz_rank_desc_idx;
DROP INDEX IF EXISTS fed_sum_standard_players_desc_idx;
DROP INDEX IF EXISTS fed_sum_rapid_players_desc_idx;
DROP INDEX IF EXISTS fed_sum_blitz_players_desc_idx;
DROP INDEX IF EXISTS fed_sum_players_desc_idx;

-- Refresh materialized view to pick up view changes
REFRESH MATERIALIZED VIEW CONCURRENTLY federations_summary;
