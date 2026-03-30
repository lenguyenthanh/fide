-- Use row_number() instead of rank() for top-10 player ranking.
-- rank() can include >10 players when there are ties; row_number()
-- always gives exactly 10, consistent with HistoryDb queries.
CREATE OR REPLACE VIEW federations_avg_top_10_ranking AS (

with fed_with_ranking AS
(SELECT federations.id, players.standard, players.rapid, players.blitz,
row_number() OVER (
    PARTITION BY federations.id
    ORDER BY players.standard DESC NULLS LAST
        ) as standard_rank,
row_number() OVER (
    PARTITION BY federations.id
    ORDER BY players.rapid DESC NULLS LAST
        ) as rapid_rank,
row_number() OVER (
    PARTITION BY federations.id
    ORDER BY players.blitz DESC NULLS LAST
        ) as blitz_rank
FROM players, federations
WHERE players.federation_id = federations.id AND players.active = true),

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

REFRESH MATERIALIZED VIEW CONCURRENTLY federations_summary;
