package fide.cli

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import fide.broadcast.{ LichessClient, LiveRatingIngester }
import fide.db.LiveRatingDb
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.Instant
import java.time.ZoneOffset

/** `fide-cli live-ratings backfill` — one-shot catch-up for finished broadcast rounds
  * in the current FIDE cycle that the backend tick hasn't picked up yet (design CLI §4).
  *
  * Two-phase flow:
  *   - Phase 1 (skipped if `--tour-id` is set): paginate `/api/broadcast/top` up to 20
  *     pages, collecting tour IDs from `past.currentPageResults` + `active[]`. Stop
  *     when `nextPage == null`, page > 20, or every tour's end date is before cutoff.
  *   - Phase 2: for each tour, fetch the full rounds list via `/api/broadcast/{tourId}`,
  *     filter `rated && finished && unprocessed-or-tuple-changed`, and hand to
  *     `LiveRatingIngester.ingestRound` under a shared lock held for the whole run.
  *
  * Emits a structured summary log line at the end (design decision #19).
  */
object LiveRatingsBackfill:

  def run(config: BackfillConfig)(using Logger[IO]): IO[Unit] =
    CliResources.makeForBackfill(config).use: (mainDb, liveRatingDb, lockService, client) =>
      given Random[IO] = Random.javaUtilConcurrentThreadLocalRandom[IO]
      import LichessClient.given // Raise[IO, LichessError]

      val hostName  = java.net.InetAddress.getLocalHost.getHostName
      val pid       = java.lang.ProcessHandle.current().pid()
      val holder    = s"backfill:$pid@$hostName"

      val lichess   = LichessClient(client, config.lichess)
      val ingester  = LiveRatingIngester(
        lichess,
        ids => mainDb.playersByFideIds(ids),
        liveRatingDb
      )

      val cutoff: Option[Instant] =
        (config.cli.since, config.cli.tourId) match
          case (_, Some(_))      => None // --tour-id ignores cutoff (decision CLI #15)
          case (Some(date), _)   => Some(date.atStartOfDay(ZoneOffset.UTC).toInstant)
          case _                 => None // v1: no monthly-cutoff integration yet — TODO

      for
        _        <- preflight(holder, config, cutoff)
        startedAt <- IO.realTimeInstant
        _        <- info"backfill: acquiring lock as $holder"
        result   <- lockService
          .acquireBlocking(holder, onWaiting = current => info"backfill: waiting for lock (held by $current)")
          .use: _ =>
            runWithLock(lichess, ingester, liveRatingDb, holder, config, cutoff)
        endedAt  <- IO.realTimeInstant
        duration  = java.time.Duration.between(startedAt, endedAt).toSeconds
        _        <- summary(config, result, duration, cutoff)
      yield ()

  // ------------------------------------------------------------
  // Summary
  // ------------------------------------------------------------

  private case class BackfillResult(
      roundsIngested: Int,
      roundsSkippedAlreadyIngested: Int,
      roundsSkippedOther: Int,
      toursSeen: Int
  )

  private def preflight(holder: String, config: BackfillConfig, cutoff: Option[Instant])(using Logger[IO]): IO[Unit] =
    info"backfill preflight: lichess_base=${config.lichess.baseUri}, db_host=${config.postgres.host}, cutoff=${cutoff.map(_.toString).getOrElse("none")}, tour_id=${config.cli.tourId.getOrElse("none")}, dry_run=${config.cli.dryRun}, holder=$holder"

  private def summary(
      config: BackfillConfig,
      result: BackfillResult,
      durationSec: Long,
      cutoff: Option[Instant]
  )(using Logger[IO]): IO[Unit] =
    info"live-rating-backfill summary: rounds_ingested=${result.roundsIngested} rounds_skipped_already_ingested=${result.roundsSkippedAlreadyIngested} rounds_skipped_other=${result.roundsSkippedOther} tours_seen=${result.toursSeen} duration_s=$durationSec cutoff=${cutoff.map(_.toString).getOrElse("none")} tour_id=${config.cli.tourId.getOrElse("none")} exit_status=success dry_run=${config.cli.dryRun}"

  // ------------------------------------------------------------
  // Main flow with lock held
  // ------------------------------------------------------------

  private def runWithLock(
      client: LichessClient[IO],
      ingester: LiveRatingIngester,
      liveRatingDb: LiveRatingDb,
      holder: String,
      config: BackfillConfig,
      cutoff: Option[Instant]
  )(using Logger[IO]): IO[BackfillResult] =
    for
      tourIds <- config.cli.tourId match
        case Some(id) => List(id).pure[IO]
        case None     => phaseOneDiscovery(client, cutoff)
      _       <- info"backfill phase 1 done: ${tourIds.size} tour(s) to fetch"
      result  <- phaseTwoIngest(client, ingester, liveRatingDb, holder, config, cutoff, tourIds)
    yield result

  // ------------------------------------------------------------
  // Phase 1 — /api/broadcast/top pagination
  // ------------------------------------------------------------

  private val MaxTopPages = 20

  private def phaseOneDiscovery(
      client: LichessClient[IO],
      cutoff: Option[Instant]
  )(using Logger[IO]): IO[List[String]] =
    def loop(page: Int, acc: List[String]): IO[List[String]] =
      if page > MaxTopPages then
        warn"backfill phase 1: hit 20-page Lichess ceiling; older rounds may be unreachable — use --since to target earlier dates"
          .as(acc)
      else
        client.fetchTop(page).flatMap: resp =>
          val newTourIds =
            (resp.active ++ resp.past.currentPageResults)
              .filter(b => !isEndedBeforeCutoff(b.tour.dates, cutoff))
              .map(_.tour.id)
          val merged   = acc ++ newTourIds
          val continue = resp.past.nextPage.isDefined &&
            resp.past.currentPageResults.exists(b => !isEndedBeforeCutoff(b.tour.dates, cutoff))
          if continue then loop(page + 1, merged) else merged.pure[IO]

    loop(1, Nil).map(_.distinct)

  private def isEndedBeforeCutoff(dates: Option[List[Long]], cutoff: Option[Instant]): Boolean =
    (dates.flatMap(_.lift(1)), cutoff) match
      case (Some(endMs), Some(c)) => Instant.ofEpochMilli(endMs).isBefore(c)
      case _                      => false

  // ------------------------------------------------------------
  // Phase 2 — per-tour ingest with re-finalization tuple check
  // ------------------------------------------------------------

  private def phaseTwoIngest(
      client: LichessClient[IO],
      ingester: LiveRatingIngester,
      liveRatingDb: LiveRatingDb,
      holder: String,
      config: BackfillConfig,
      cutoff: Option[Instant],
      tourIds: List[String]
  )(using Logger[IO]): IO[BackfillResult] =
    val initial = BackfillResult(0, 0, 0, tourIds.size)
    tourIds.foldLeftM(initial): (acc, tourId) =>
      ingestOneTour(client, ingester, liveRatingDb, holder, config, cutoff, tourId, acc)

  private def ingestOneTour(
      client: LichessClient[IO],
      ingester: LiveRatingIngester,
      liveRatingDb: LiveRatingDb,
      holder: String,
      config: BackfillConfig,
      cutoff: Option[Instant],
      tourId: String,
      acc: BackfillResult
  )(using Logger[IO]): IO[BackfillResult] =
    client.fetchTour(tourId).flatMap: resp =>
      val candidates = resp.rounds.filter: r =>
        r.rated && r.finished.contains(true) && passesCutoff(r.finishedAt, cutoff)
      val observations = candidates.map: r =>
        (r.id, r.finishedAt.map(ms => Instant.ofEpochMilli(ms)), r.rated)

      liveRatingDb.filterUnprocessedRounds(observations).flatMap: toIngest =>
        val toIngestSet = toIngest.toSet
        val targets = candidates.collect:
          case r if toIngestSet.contains(r.id) =>
            LiveRatingIngester.RoundTarget(
              tourId = resp.tour.id,
              roundId = r.id,
              tourSlug = resp.tour.slug,
              roundSlug = r.slug,
              finishedAt = r.finishedAt.map(ms => Instant.ofEpochMilli(ms)),
              rated = r.rated
            )
        val alreadyIngested = candidates.size - targets.size

        if config.cli.dryRun then
          targets
            .traverse_(t => info"[dry-run] would ingest round ${t.roundId} (tour $tourId)")
            .as(acc.copy(roundsIngested = acc.roundsIngested + targets.size, roundsSkippedAlreadyIngested = acc.roundsSkippedAlreadyIngested + alreadyIngested))
        else
          Ref
            .of[IO, Set[String]](Set.empty)
            .flatMap: unknown =>
              targets
                .foldLeftM((0, 0)): (counts, target) =>
                  val (ingested, failed) = counts
                  ingester
                    .ingestRound(holder, target, unknown)
                    .as((ingested + 1, failed))
                    .handleErrorWith: e =>
                      warn"backfill: round ${target.roundId} failed: ${e.getMessage}".as((ingested, failed + 1))
                .map: (ingested, failed) =>
                  acc.copy(
                    roundsIngested = acc.roundsIngested + ingested,
                    roundsSkippedAlreadyIngested = acc.roundsSkippedAlreadyIngested + alreadyIngested,
                    roundsSkippedOther = acc.roundsSkippedOther + failed
                  )
    .handleErrorWith: e =>
      warn"backfill: tour $tourId fetch failed: ${e.getMessage}".as(
        acc.copy(roundsSkippedOther = acc.roundsSkippedOther + 1)
      )

  private def passesCutoff(finishedAt: Option[Long], cutoff: Option[Instant]): Boolean =
    (finishedAt, cutoff) match
      case (Some(ms), Some(c)) => !Instant.ofEpochMilli(ms).isBefore(c)
      case _                   => true
