package fide
package broadcast

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import fide.broadcast.LichessDtos.*
import fide.db.IngestedRoundRow
import fide.db.LiveRatingDb
import fide.db.LiveRatingDb.LockLostException
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.Instant
import scala.concurrent.duration.*

/** Orchestrates ingestion of a single broadcast round.
  *
  * One call = one round fully ingested or fully rolled back. See design §9:
  *   1. Fetch the round via `LichessClient`.
  *   2. Resolve each player's FIDE ID to our internal `PlayerId` (log unknowns deduped
  *      per tick via the caller-provided `unknownFideIds: Ref[IO, Set[String]]`).
  *   3. Compute Elo diffs via `EloService` (pure).
  *   4. Hand the assembled batch to `LiveRatingDb.ingestRoundAtomically`.
  *
  * DB transient-error retry (SQLSTATE 40001 / 40P01) is wrapped at this layer per
  * decision #31, so serializable-isolation conflicts and deadlocks bounce back cleanly.
  */
trait LiveRatingIngester:
  def ingestRound(
      holder: String,
      summary: LiveRatingIngester.RoundTarget,
      unknownFideIds: Ref[IO, Set[String]]
  ): IO[Unit]

object LiveRatingIngester:

  /** Minimal metadata we need from discovery to ingest a round. Sourced from the
    * backend tick's `/api/broadcast` discovery (inline rounds) or the CLI's Phase 2
    * per-tour fetch.
    *
    * `tourSlug` and `roundSlug` may be `"-"` per the Lichess spec when only the id is known.
    */
  final case class RoundTarget(
      tourId: String,
      roundId: String,
      tourSlug: String,
      roundSlug: String,
      finishedAt: Option[Instant],
      rated: Boolean
  )

  def apply(
      client: LichessClient[IO],
      playersByFideIds: Set[FideId] => IO[List[PlayerInfo]],
      db: LiveRatingDb
  )(using Logger[IO], Random[IO]): LiveRatingIngester = new:

    def ingestRound(
        holder: String,
        summary: RoundTarget,
        unknownFideIds: Ref[IO, Set[String]]
    ): IO[Unit] =
      for
        resp       <- client.fetchRound(summary.tourSlug, summary.roundSlug, summary.roundId)
        tc          = resolveTc(resp.tour)
        rows       <- buildRows(summary, resp, tc, unknownFideIds)
        cursor      = IngestedRoundRow(
          roundId = summary.roundId,
          tourId = summary.tourId,
          gameCount = rows.size,
          ingestedAt = Instant.now(),
          finishedAtObserved = summary.finishedAt,
          ratedObserved = summary.rated,
          failureCount = 0,
          failedAt = None
        )
        _          <- withDbRetry(db.ingestRoundAtomically(holder, rows, cursor))
        _          <- info"ingested round ${summary.roundId}: ${rows.size} games, tc=$tc"
      yield ()

    // ------------------------------------------------------------
    // Row construction
    // ------------------------------------------------------------

    /** Build `LiveRatingGameRow`s from a fetched round. Skips unfinished games (status = "*")
      * per decision #37. Drops rows where neither side could be resolved (DB CHECK constraint).
      */
    private def buildRows(
        target: RoundTarget,
        resp: BroadcastRoundResponse,
        tcOpt: Option[TimeControl],
        unknownFideIds: Ref[IO, Set[String]]
    ): IO[List[LiveRatingGameRow]] =
      tcOpt match
        case None =>
          warn"round ${target.roundId}: no fideTC on tour ${target.tourId}; skipping".as(Nil)
        case Some(tc) =>
          // Collect every FIDE ID present in the round, look them up in bulk once.
          val fideIds: Set[FideId] = resp.games
            .flatMap(_.players.getOrElse(Nil))
            .flatMap(_.fideId)
            .flatMap(i => FideId.option(i.toString))
            .toSet
          for
            players   <- if fideIds.isEmpty then IO.pure(List.empty[PlayerInfo])
                         else playersByFideIds(fideIds)
            byFideId   = players.flatMap(p => p.fideId.map(_ -> p)).toMap
            finishedAt = target.finishedAt
            rows      <- resp.games.traverse(g => rowForGame(target, g, tc, byFideId, unknownFideIds, finishedAt))
          yield rows.flatten

    private def rowForGame(
        target: RoundTarget,
        game: BroadcastRoundGame,
        tc: TimeControl,
        byFideId: Map[FideId, PlayerInfo],
        unknownFideIds: Ref[IO, Set[String]],
        finishedAt: Option[Instant]
    ): IO[Option[LiveRatingGameRow]] =
      parseResult(game.status) match
        case None         => IO.pure(None)
        case Some(result) =>
          val players = game.players.getOrElse(Nil)
          val white   = players.headOption
          val black   = players.drop(1).headOption
          for
            whiteRes <- resolveSide(white, byFideId, unknownFideIds, target)
            blackRes <- resolveSide(black, byFideId, unknownFideIds, target)
          yield (whiteRes, blackRes) match
            case (Unresolvable, Unresolvable) =>
              // Neither side playable — violates CHECK; drop.
              None
            case (ws, bs) =>
              val (whiteDiff, blackDiff) = computeDiffs(tc, ws, bs, result)
              Some(
                LiveRatingGameRow(
                  gameId = game.id,
                  tourId = target.tourId,
                  roundId = target.roundId,
                  timeControl = tc,
                  whitePlayerId = ws.playerId,
                  blackPlayerId = bs.playerId,
                  whiteFideId = ws.fideIdRaw,
                  blackFideId = bs.fideIdRaw,
                  whiteRatingDiff = whiteDiff,
                  blackRatingDiff = blackDiff,
                  result = result,
                  roundFinishedAt = finishedAt
                )
              )

    // ------------------------------------------------------------
    // Side resolution — carries info we need for Elo math + audit.
    // ------------------------------------------------------------

    private sealed trait SideResolution:
      def playerId: Option[PlayerId]
      def fideIdRaw: Option[String]
      def rating: Option[Int]
      def kFactor: Option[Int]

    private case class Resolved(
        player: PlayerInfo,
        rating: Option[Int],
        kFactor: Option[Int]
    ) extends SideResolution:
      def playerId: Option[PlayerId]  = Some(player.id)
      def fideIdRaw: Option[String]   = player.fideId.map(_.value)

    private case class Unknown(
        fideIdRaw: Option[String],
        rating: Option[Int]
    ) extends SideResolution:
      def playerId: Option[PlayerId] = None
      def kFactor: Option[Int]        = None

    private case object Unresolvable extends SideResolution:
      def playerId: Option[PlayerId] = None
      def fideIdRaw: Option[String]  = None
      def rating: Option[Int]        = None
      def kFactor: Option[Int]       = None

    private def resolveSide(
        p: Option[BroadcastRoundGamePlayer],
        byFideId: Map[FideId, PlayerInfo],
        unknownFideIds: Ref[IO, Set[String]],
        target: RoundTarget
    ): IO[SideResolution] = p match
      case None => IO.pure(Unresolvable)
      case Some(player) =>
        player.fideId.flatMap(i => FideId.option(i.toString)) match
          case None =>
            IO.pure(Unknown(player.fideId.map(_.toString), player.rating))
          case Some(fid) =>
            byFideId.get(fid) match
              case Some(info) =>
                IO.pure(Resolved(info, player.rating.orElse(perTcRating(info, currentTc = None)), perTcK(info)))
              case None =>
                logUnknownOnce(fid.value, target, unknownFideIds).as(
                  Unknown(Some(fid.value), player.rating)
                )

    private def logUnknownOnce(
        fideId: String,
        target: RoundTarget,
        ref: Ref[IO, Set[String]]
    ): IO[Unit] =
      ref.modify(s => if s.contains(fideId) then (s, false) else (s + fideId, true)).flatMap:
        case false => IO.unit
        case true =>
          warn"unknown fideId $fideId seen in round ${target.roundId} (tour ${target.tourId})"

    // ------------------------------------------------------------
    // Elo — per-TC rating + K-factor lookup, then delegate to EloService.
    // ------------------------------------------------------------

    private def computeDiffs(
        tc: TimeControl,
        white: SideResolution,
        black: SideResolution,
        result: GameResult
    ): (Option[Int], Option[Int]) =
      val whiteResolved = resolvedFor(tc, white)
      val blackResolved = resolvedFor(tc, black)
      (whiteResolved, blackResolved) match
        case (Some((wR, wK)), Some((bR, bK))) =>
          val (wd, bd) = EloService.computeDiffs(tc, wR, wK, bR, bK, result)
          (white.playerId.map(_ => wd), black.playerId.map(_ => bd))
        case _ =>
          // Missing ratings on either side → no diff can be attributed.
          (None, None)

    private def resolvedFor(tc: TimeControl, side: SideResolution): Option[(Int, Int)] =
      side match
        case Resolved(info, _, _) =>
          val rating = tc match
            case TimeControl.Standard => info.standard.map(_.toInt)
            case TimeControl.Rapid    => info.rapid.map(_.toInt)
            case TimeControl.Blitz    => info.blitz.map(_.toInt)
          val k = tc match
            case TimeControl.Standard => info.standardK
            case TimeControl.Rapid    => info.rapidK
            case TimeControl.Blitz    => info.blitzK
          rating.map(r => (r, k.getOrElse(EloService.DefaultKFactor)))
        case Unknown(_, Some(r)) =>
          Some((r, EloService.DefaultKFactor))
        case _ => None

    private def perTcRating(info: PlayerInfo, currentTc: Option[TimeControl]): Option[Int] =
      currentTc match
        case Some(TimeControl.Standard) => info.standard.map(_.toInt)
        case Some(TimeControl.Rapid)    => info.rapid.map(_.toInt)
        case Some(TimeControl.Blitz)    => info.blitz.map(_.toInt)
        case None                       => info.standard.map(_.toInt) // fallback; unused in current code path

    private def perTcK(info: PlayerInfo): Option[Int] = info.standardK

    // ------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------

    /** Lichess reports "*" / "1-0" / "0-1" / "½-½" (Unicode 1/2). Map to our enum,
      * or `None` to skip the game (decision #37: only decisive results are ingested).
      */
    private def parseResult(status: Option[String]): Option[GameResult] =
      status.flatMap:
        case "1-0"     => Some(GameResult.WhiteWin)
        case "0-1"     => Some(GameResult.BlackWin)
        case "1/2-1/2" => Some(GameResult.Draw)
        case "½-½"     => Some(GameResult.Draw)
        case _         => None // "*" or anything else

    private def resolveTc(tour: BroadcastTour): Option[TimeControl] =
      tour.info.flatMap(_.fideTC).flatMap(TimeControl.apply)

    // ------------------------------------------------------------
    // DB transient-error retry — SQLSTATE 40001 (serialization) / 40P01 (deadlock).
    // Design decision #31. Same 1s/2s/4s budget as HTTP, no jitter needed here
    // (single-writer domain, no thundering herd).
    // ------------------------------------------------------------

    private def withDbRetry[A](fa: IO[A]): IO[A] =
      RetryHelper.retryOn[IO, A](
        maxRetries = 3,
        baseDelay = 1.second,
        logRetries = true
      )(isDbTransient)(fa)

    private def isDbTransient(err: Throwable): Boolean = err match
      case _: LockLostException => false // fatal; caller must abort
      case e: skunk.exception.PostgresErrorException =>
        e.code == "40001" || e.code == "40P01"
      case _ => false
