package fide
package broadcast

import cats.effect.*
import cats.effect.syntax.all.*
import fide.db.LockService
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.Instant

/** Single tick of the live-rating ingest loop (design §9 / decision #52).
  *
  * Flow:
  *   1. Try to acquire the live-rating ingest lock (`LockService.tryAcquire`).
  *      If held by someone else (CLI backfill, monthly reset) → log info and skip.
  *      This is NOT a "failed tick" for the circuit breaker (decision #30).
  *   2. With the lock held (heartbeat fiber running in background):
  *        - Stream official broadcasts from `/api/broadcast`.
  *        - Flatten to rounds, filter `rated && finished && (new OR tuple-changed)`.
  *        - `parTraverseN(maxConcurrent)(ingester.ingestRound)`.
  *   3. Typed Lichess errors raised inside are rescued at the tick boundary —
  *      429 / HTTP / decode failures are logged at warn; the tick ends cleanly.
  */
trait LiveRatingSyncer:
  /** Run one tick. Returns `Outcome` so the job loop can track the circuit breaker. */
  def tick: IO[LiveRatingSyncer.Outcome]

object LiveRatingSyncer:

  enum Outcome:
    case Done(rounds: Int)
    case Skipped
    case Failed(error: Throwable)

  def apply(
      client: LichessClient[IO],
      ingester: LiveRatingIngester,
      db: fide.db.LiveRatingDb,
      lockService: LockService,
      holder: String,
      maxConcurrentRounds: Int
  )(using Logger[IO]): LiveRatingSyncer = new:

    def tick: IO[Outcome] =
      lockService.tryAcquire(holder).use:
        case None =>
          info"live-rating tick skipped (lock held by another process)".as(Outcome.Skipped)
        case Some(_) =>
          tickInner.handleErrorWith:
            case LichessClient.LichessErrorException(err) =>
              warn"live-rating tick: lichess error; tick ends: $err".as(Outcome.Failed(LichessClient.LichessErrorException(err)))
            case err                                      =>
              error"live-rating tick: unexpected error: ${err.getMessage}".as(Outcome.Failed(err))

    private def tickInner: IO[Outcome] =
      for
        _       <- info"live-rating tick starting"
        started  = Instant.now()
        targets <- discover
        _       <- info"live-rating tick: ${targets.size} new/changed rounds to ingest"
        unknown <- Ref.of[IO, Set[String]](Set.empty)
        _       <- targets.parTraverseN(maxConcurrentRounds): target =>
                     ingester.ingestRound(holder, target, unknown).handleErrorWith: e =>
                       warn"live-rating tick: round ${target.roundId} failed: ${e.getMessage}"
        elapsed  = java.time.Duration.between(started, Instant.now()).toMillis
        _       <- info"live-rating tick done: ${targets.size} rounds ingested in ${elapsed}ms"
      yield Outcome.Done(targets.size)

    private def discover: IO[List[LiveRatingIngester.RoundTarget]] =
      for
        broadcasts <- client.listBroadcasts.compile.toList
        // Flatten to (tour, round) pairs
        pairs       = broadcasts.flatMap(b => b.rounds.map(r => (b.tour, r)))
        // Filter: rated && finished
        candidates  = pairs.collect:
          case (tour, r) if r.rated && r.finished.contains(true) =>
            (tour, r, r.finishedAt.map(ms => Instant.ofEpochMilli(ms)))
        // Ask DB which rounds are new or tuple-changed
        roundsForDb = candidates.map((_, r, fa) => (r.id, fa, r.rated))
        unprocessed <- db.filterUnprocessedRounds(roundsForDb)
        unprocessedSet = unprocessed.toSet
      yield candidates.collect:
        case (tour, r, fa) if unprocessedSet.contains(r.id) =>
          LiveRatingIngester.RoundTarget(
            tourId = tour.id,
            roundId = r.id,
            tourSlug = tour.slug,
            roundSlug = r.slug,
            finishedAt = fa,
            rated = r.rated
          )
