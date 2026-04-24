package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.db.LiveRatingCodecs.IngestedRoundRow
import fide.domain.Models.*
import fide.types.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import java.time.Instant

/** Persistence surface for the live-rating projection (design §9 / §6).
  *
  * This is the only layer that knows about the `live_rating_games` / `live_ratings` /
  * `ingested_rounds` / `live_rating_ingest_lock` tables. Consumers (backend tick, CLI
  * backfill, monthly-reset hook) call through here.
  *
  * All writing methods include a lock-holder guard predicate (see decision #52) that
  * aborts via `LiveRatingError.LockLost` if the caller no longer owns the ingest lock
  * by the time the transaction commits.
  */
trait LiveRatingDb:

  /** Ingest one finished broadcast round atomically. Three batched statements in one
    * transaction:
    *   1. `INSERT` the games (idempotent via `ON CONFLICT (game_id) DO NOTHING`).
    *   2. `UPSERT` the cursor row, merging with existing `(finished_at_observed, rated_observed)`.
    *   3. Recompute aggregated `live_ratings` for the players touched by this round.
    *
    * @param holder        caller's lock-holder identifier (e.g. "tick:host" / "backfill:pid@host").
    * @param games         rows to insert.
    * @param cursorRow     cursor metadata for this round.
    * @return              fails with `LiveRatingError.LockLost` if the guard predicate
    *                      observed the lock has expired or been taken by someone else.
    */
  def ingestRoundAtomically(
      holder: String,
      games: List[LiveRatingGameRow],
      cursorRow: IngestedRoundRow
  ): IO[Unit]

  /** Wipe all three live-rating tables. Called by the monthly-reset hook
    * after the FIDE monthly ingest commits (design #17/#52).
    */
  def truncateAll(holder: String): IO[Unit]

  /** Lookup a player's live-rating summary by internal id.
    * Returns `None` if the player has no row in `live_ratings`.
    */
  def getLiveRating(playerId: PlayerId): IO[Option[LiveRating]]

  /** Detail view: summary + up to 100 most-recent contributing games, ordered by
    * `round_finished_at DESC` (design decision #41).
    */
  def getLiveRatingDetails(playerId: PlayerId): IO[Option[LiveRatingDetails]]

  /** Given a list of rounds observed on Lichess `(id, finishedAt, rated)`, return the subset
    * we need to ingest: rounds not yet in `ingested_rounds`, plus rounds whose stored
    * `(finished_at_observed, rated_observed)` tuple differs — the re-finalization case
    * (design decision #29 / CLI decision #14).
    */
  def filterUnprocessedRounds(
      rounds: List[(String, Option[Instant], Boolean)]
  ): IO[List[String]]

object LiveRatingDb:

  def apply(postgres: Resource[IO, Session[IO]]): LiveRatingDb = new:

    def ingestRoundAtomically(
        holder: String,
        games: List[LiveRatingGameRow],
        cursorRow: IngestedRoundRow
    ): IO[Unit] =
      val touched = games
        .flatMap(g => List(g.whitePlayerId, g.blackPlayerId))
        .flatten
        .distinct
        .sortBy(_.value)
      postgres.use: session =>
        session.transaction.use: _ =>
          for
            _ <- Sql.assertLockHeld(session, holder)
            _ <- session
              .prepare(Sql.insertGamesBatch(games.size))
              .flatMap(_.execute(games))
              .whenA(games.nonEmpty)
            _ <- session.execute(Sql.upsertIngestedRound)(cursorRow)
            _ <- session
              .prepare(Sql.recomputeLiveRatingsBatch(touched.size))
              .flatMap(_.execute(touched))
              .whenA(touched.nonEmpty)
          yield ()

    def truncateAll(holder: String): IO[Unit] =
      postgres.use: session =>
        session.transaction.use: _ =>
          Sql.assertLockHeld(session, holder) *>
            session.execute(Sql.truncateAll).void

    def getLiveRating(playerId: PlayerId): IO[Option[LiveRating]] =
      postgres.use(_.option(Sql.getLiveRating)(playerId))

    def getLiveRatingDetails(playerId: PlayerId): IO[Option[LiveRatingDetails]] =
      postgres.use: session =>
        for
          summary <- session.option(Sql.getLiveRating)(playerId)
          games   <- session.execute(Sql.getContributingGames)(playerId)
        yield summary.map(s => LiveRatingDetails(s, games))

    def filterUnprocessedRounds(
        rounds: List[(String, Option[Instant], Boolean)]
    ): IO[List[String]] =
      if rounds.isEmpty then IO.pure(Nil)
      else
        postgres.use: session =>
          session
            .prepare(Sql.existingRounds(rounds.size))
            .flatMap(_.stream(rounds.map(_._1), 64).compile.toList)
            .map: existing =>
              // Unprocessed = (not in existing) OR (tuple differs from observed).
              val existingMap = existing.map(r => r.roundId -> (r.finishedAtObserved, r.ratedObserved)).toMap
              rounds.collect:
                case (rid, fa, rated) if existingMap.get(rid) match
                    case None                     => true
                    case Some((storedFa, storedR)) =>
                      storedFa != fa || storedR != rated
                  => rid

  // ============================================================
  // SQL
  // ============================================================

  private object Sql:

    import DbCodecs.*
    import LiveRatingCodecs.*

    /** Lock-holder guard predicate (design #52). Returns nothing from the DB; raises
      * `LiveRatingError.LockLost` in IO if the caller doesn't hold the live lock.
      *
      * Single-statement check: the UPDATE refreshes `expires_at` only if `holder = $me`
      * and the lock hasn't expired. Zero rows updated = lost → raise.
      */
    def assertLockHeld(session: Session[IO], holder: String): IO[Unit] =
      session.prepare(lockGuardUpdate).flatMap(_.execute(holder)).flatMap: completion =>
        completion match
          case skunk.data.Completion.Update(1) => IO.unit
          case _                               => IO.raiseError(LockLostException(holder))

    val lockGuardUpdate: Command[String] =
      sql"""
        UPDATE live_rating_ingest_lock
           SET expires_at = NOW() + interval '120 seconds'
         WHERE lock_name = 'live_rating_ingest'
           AND holder = $text
           AND expires_at > NOW()
      """.command

    // ------------------------------------------------------------
    // Games ledger — batch INSERT
    // ------------------------------------------------------------

    def insertGamesBatch(n: Int): Command[List[LiveRatingGameRow]] =
      val rows = liveRatingGameRow.values.list(n)
      sql"""
        INSERT INTO live_rating_games (
          game_id, broadcast_tour_id, broadcast_round_id, time_control,
          white_player_id, black_player_id,
          white_fide_id, black_fide_id,
          white_rating_diff, black_rating_diff,
          result, round_finished_at
        )
        VALUES $rows
        ON CONFLICT (game_id) DO NOTHING
      """.command

    // ------------------------------------------------------------
    // Cursor row UPSERT — keyed on round_id. We always bump the observed
    // tuple to the latest values; the CLI/tick compares against this when
    // deciding whether to re-ingest (design #29).
    // ------------------------------------------------------------

    val upsertIngestedRound: Command[IngestedRoundRow] =
      sql"""
        INSERT INTO ingested_rounds (
          round_id, tour_id, game_count, ingested_at,
          finished_at_observed, rated_observed, failure_count, failed_at
        )
        VALUES $ingestedRoundRow
        ON CONFLICT (round_id) DO UPDATE SET
          tour_id              = EXCLUDED.tour_id,
          game_count           = EXCLUDED.game_count,
          ingested_at          = EXCLUDED.ingested_at,
          finished_at_observed = EXCLUDED.finished_at_observed,
          rated_observed       = EXCLUDED.rated_observed
      """.command

    // ------------------------------------------------------------
    // Recompute aggregation for the touched players.
    //
    // Design decision #28: the OR-join on (white_player_id = pi.id OR black_player_id = pi.id)
    // was rejected as non-sargable. This uses a per-side UNION ALL CTE so each arm can use
    // its partial index (live_rating_games_{white,black}_tc_idx).
    //
    // One statement:
    //   1. `touched`  — the player_ids we just wrote games for.
    //   2. `per_side` — games unioned by side (player_id, tc, diff).
    //   3. `agg`      — SUM/COUNT per (player_id, time_control).
    //   4. INSERT-SELECT pivots the per-TC rows into a single row per player,
    //      joins to `players` for official rating, and computes projected_*.
    //   5. `ON CONFLICT (player_id) DO UPDATE SET ...` keeps it idempotent.
    //
    // Players that end up with zero contributions after recompute (shouldn't happen when
    // called from `ingestRoundAtomically` since we only pass players who had at least one
    // game this tick, but could happen after re-finalization deletes): the monthly reset
    // or a separate cleanup path handles them. Per CHECK constraint, at least one TC must
    // be non-null; the `WHERE` below filters out the zero-sum case.
    // ------------------------------------------------------------

    def recomputeLiveRatingsBatch(n: Int): Command[List[PlayerId]] =
      val ids = playerIdCodec.values.list(n)
      sql"""
        WITH touched AS (
          SELECT pi.id AS player_id
          FROM player_info pi
          WHERE pi.id IN ($ids)
        ),
        per_side AS (
          SELECT g.white_player_id AS player_id, g.time_control, g.white_rating_diff AS diff
          FROM live_rating_games g
          WHERE g.white_player_id IN (SELECT player_id FROM touched)
            AND g.white_rating_diff IS NOT NULL
          UNION ALL
          SELECT g.black_player_id AS player_id, g.time_control, g.black_rating_diff AS diff
          FROM live_rating_games g
          WHERE g.black_player_id IN (SELECT player_id FROM touched)
            AND g.black_rating_diff IS NOT NULL
        ),
        agg AS (
          SELECT player_id, time_control, SUM(diff)::int AS total_diff, COUNT(*)::int AS games_count
          FROM per_side
          GROUP BY player_id, time_control
        ),
        pivoted AS (
          SELECT
            player_id,
            MAX(CASE WHEN time_control = 'standard' THEN total_diff  END) AS std_diff,
            MAX(CASE WHEN time_control = 'standard' THEN games_count END) AS std_games,
            MAX(CASE WHEN time_control = 'rapid'    THEN total_diff  END) AS rap_diff,
            MAX(CASE WHEN time_control = 'rapid'    THEN games_count END) AS rap_games,
            MAX(CASE WHEN time_control = 'blitz'    THEN total_diff  END) AS bli_diff,
            MAX(CASE WHEN time_control = 'blitz'    THEN games_count END) AS bli_games
          FROM agg
          GROUP BY player_id
        )
        INSERT INTO live_ratings (
          player_id,
          standard_diff, standard_games_played,
          rapid_diff,    rapid_games_played,
          blitz_diff,    blitz_games_played,
          projected_standard, projected_rapid, projected_blitz,
          updated_at
        )
        SELECT
          pv.player_id,
          pv.std_diff, pv.std_games,
          pv.rap_diff, pv.rap_games,
          pv.bli_diff, pv.bli_games,
          CASE WHEN pv.std_diff IS NOT NULL THEN p.standard + pv.std_diff END,
          CASE WHEN pv.rap_diff IS NOT NULL THEN p.rapid    + pv.rap_diff END,
          CASE WHEN pv.bli_diff IS NOT NULL THEN p.blitz    + pv.bli_diff END,
          NOW()
        FROM pivoted pv
        JOIN players p ON p.id = pv.player_id
        WHERE pv.std_diff IS NOT NULL
           OR pv.rap_diff IS NOT NULL
           OR pv.bli_diff IS NOT NULL
        ON CONFLICT (player_id) DO UPDATE SET
          standard_diff         = EXCLUDED.standard_diff,
          standard_games_played = EXCLUDED.standard_games_played,
          rapid_diff            = EXCLUDED.rapid_diff,
          rapid_games_played    = EXCLUDED.rapid_games_played,
          blitz_diff            = EXCLUDED.blitz_diff,
          blitz_games_played    = EXCLUDED.blitz_games_played,
          projected_standard    = EXCLUDED.projected_standard,
          projected_rapid       = EXCLUDED.projected_rapid,
          projected_blitz       = EXCLUDED.projected_blitz,
          updated_at            = EXCLUDED.updated_at
      """.command

    // ------------------------------------------------------------
    // Monthly reset — design #17/#52.
    // ------------------------------------------------------------

    val truncateAll: Command[Void] =
      sql"TRUNCATE live_rating_games, live_ratings, ingested_rounds".command

    // ------------------------------------------------------------
    // Read-side queries
    // ------------------------------------------------------------

    val getLiveRating: Query[PlayerId, LiveRating] =
      sql"""
        SELECT
          player_id,
          standard_diff, standard_games_played,
          rapid_diff,    rapid_games_played,
          blitz_diff,    blitz_games_played,
          projected_standard, projected_rapid, projected_blitz,
          updated_at
        FROM live_ratings
        WHERE player_id = $playerIdCodec
      """.query(liveRatingRow)

    /** Up to 100 most-recent contributing games for a player, either side. */
    val getContributingGames: Query[PlayerId, LiveRatingGameRow] =
      sql"""
        SELECT
          game_id, broadcast_tour_id, broadcast_round_id, time_control,
          white_player_id, black_player_id,
          white_fide_id, black_fide_id,
          white_rating_diff, black_rating_diff,
          result, round_finished_at
        FROM live_rating_games
        WHERE white_player_id = $playerIdCodec
           OR black_player_id = $playerIdCodec
        ORDER BY round_finished_at DESC NULLS LAST
        LIMIT 100
      """.query(liveRatingGameRow).contramap((id: PlayerId) => (id, id))

    def existingRounds(n: Int): Query[List[String], IngestedRoundRow] =
      val ids = text.values.list(n)
      sql"""
        SELECT round_id, tour_id, game_count, ingested_at,
               finished_at_observed, rated_observed, failure_count, failed_at
        FROM ingested_rounds
        WHERE round_id IN ($ids)
      """.query(ingestedRoundRow)

  /** Internal Throwable wrapping `LiveRatingError.LockLost`. Kept private — callers should
    * convert to typed error at the boundary.
    */
  final case class LockLostException(holder: String)
      extends RuntimeException(s"live-rating ingest lock lost for holder $holder", null, true, false)
