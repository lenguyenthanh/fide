-- ============================================================
-- Live ratings: per-game ledger + per-player aggregated projection
-- See .claude/docs/live-ratings-design.md §6.
-- ============================================================

CREATE TYPE time_control AS ENUM ('standard', 'rapid', 'blitz');

-- ============================================================
-- live_rating_games — per-game ledger (source of truth, auditable)
-- ============================================================
CREATE TABLE IF NOT EXISTS live_rating_games (
    game_id             text PRIMARY KEY,                               -- Lichess 8-char game ID
    broadcast_tour_id   text NOT NULL,
    broadcast_round_id  text NOT NULL,
    time_control        time_control NOT NULL,

    white_player_id     integer REFERENCES player_info(id) ON DELETE CASCADE,
    black_player_id     integer REFERENCES player_info(id) ON DELETE CASCADE,
    white_fide_id       text,                                           -- raw, for unresolved-player audit
    black_fide_id       text,
    white_rating_diff   integer,                                        -- computed via scalachess-rating
    black_rating_diff   integer,

    result              text NOT NULL,                                  -- '1-0' | '0-1' | '1/2-1/2'
    observed_at         timestamptz NOT NULL DEFAULT NOW(),
    round_finished_at   timestamptz,                                    -- from BroadcastRoundInfo.finishedAt

    CHECK (white_player_id IS NOT NULL OR black_player_id IS NOT NULL)
);

CREATE INDEX live_rating_games_white_tc_idx
    ON live_rating_games(white_player_id, time_control)
    WHERE white_player_id IS NOT NULL;

CREATE INDEX live_rating_games_black_tc_idx
    ON live_rating_games(black_player_id, time_control)
    WHERE black_player_id IS NOT NULL;

CREATE INDEX live_rating_games_tour_idx  ON live_rating_games(broadcast_tour_id);
CREATE INDEX live_rating_games_round_idx ON live_rating_games(broadcast_round_id);

-- ============================================================
-- live_ratings — aggregated projection (materialized from the ledger)
-- ============================================================
CREATE TABLE IF NOT EXISTS live_ratings (
    player_id               integer PRIMARY KEY REFERENCES player_info(id) ON DELETE CASCADE,

    standard_diff           integer,
    standard_games_played   integer,
    rapid_diff              integer,
    rapid_games_played      integer,
    blitz_diff              integer,
    blitz_games_played      integer,

    -- Denormalized projected rating = players.{tc} + diff, indexed for leaderboard sort.
    projected_standard      integer,
    projected_rapid         integer,
    projected_blitz         integer,

    updated_at              timestamptz NOT NULL DEFAULT NOW(),

    CHECK ((standard_diff IS NULL) = (standard_games_played IS NULL)),
    CHECK ((rapid_diff    IS NULL) = (rapid_games_played    IS NULL)),
    CHECK ((blitz_diff    IS NULL) = (blitz_games_played    IS NULL)),
    CHECK (
        standard_diff IS NOT NULL OR
        rapid_diff    IS NOT NULL OR
        blitz_diff    IS NOT NULL
    )
);

CREATE INDEX live_ratings_projected_standard_idx
    ON live_ratings(projected_standard DESC NULLS LAST);

CREATE INDEX live_ratings_projected_rapid_idx
    ON live_ratings(projected_rapid DESC NULLS LAST);

CREATE INDEX live_ratings_projected_blitz_idx
    ON live_ratings(projected_blitz DESC NULLS LAST);

-- ============================================================
-- ingested_rounds — cursor to skip already-processed finished rounds.
-- Stores (finished_at_observed, rated_observed) per main design decision #29
-- for re-finalization detection.
-- ============================================================
CREATE TABLE IF NOT EXISTS ingested_rounds (
    round_id                text PRIMARY KEY,
    tour_id                 text NOT NULL,
    game_count              integer NOT NULL,
    ingested_at             timestamptz NOT NULL DEFAULT NOW(),
    finished_at_observed    timestamptz,      -- Lichess round.finishedAt when we ingested
    rated_observed          boolean NOT NULL, -- Lichess round.rated when we ingested
    failure_count           integer NOT NULL DEFAULT 0,
    failed_at               timestamptz       -- last failure timestamp (for slug-change 404 handling)
);

CREATE INDEX ingested_rounds_tour_idx ON ingested_rounds(tour_id);

-- ============================================================
-- live_rating_ingest_lock — cross-process lock-table (main design #52).
-- Serializes backend tick, CLI backfill, and monthly reset paths.
-- Exactly one row; lock_name is a sentinel.
-- ============================================================
CREATE TABLE IF NOT EXISTS live_rating_ingest_lock (
    lock_name   text PRIMARY KEY,
    holder      text NOT NULL,              -- 'tick:<host>' | 'backfill:<pid>@<host>' | 'monthly_reset'
    acquired_at timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL
);
