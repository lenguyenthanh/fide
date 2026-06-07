package fide
package db

import cats.effect.*
import fide.domain.*
import fide.types.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait Ingestor:
  def ingest: IO[Unit]

object Ingestor:

  private val BatchSize = Db.MaxBatchSize

  def apply(
      eventDb: PlayerEventDb,
      db: Db,
      playerHashCache: HashCache[FideId]
  )(using Logger[IO]): Ingestor = new:

    def ingest: IO[Unit] =
      for
        _       <- info"Starting ingestion"
        startAt <- IO.monotonic
        total   <- eventDb
          .ungestedStream(BatchSize)
          .chunkN(BatchSize)
          .evalScan(0L): (acc, chunk) =>
            val events = chunk.toList
            info"Processing batch of ${events.size} events (total so far: $acc)" *>
              processBatch(events).as(acc + events.size)
          .compile
          .lastOrError
        elapsed <- IO.monotonic.map(_ - startAt)
        _       <- info"Ingestion complete, total=$total, duration=${elapsed.toSeconds}s"
        _       <- eventDb.purgeOld
      yield ()

    private def processBatch(events: List[PlayerEvent]): IO[Unit] =
      case class Acc(
          crawlPlayers: List[(CrawlPlayer, Option[NewFederation], Long)],
          skippedCount: Int
      )

      val ymOpt = events.headOption.flatMap(_.sourceLastModified.flatMap(YearMonth.fromLastModified))

      val acc = events.foldLeft(Acc(Nil, 0)): (acc, e) =>
        val player = CrawlPlayer(
          fideId = e.fideId,
          name = e.name,
          title = e.title,
          womenTitle = e.womenTitle,
          otherTitles = e.otherTitles,
          standard = e.standard,
          standardK = e.standardK,
          rapid = e.rapid,
          rapidK = e.rapidK,
          blitz = e.blitz,
          blitzK = e.blitzK,
          gender = e.gender,
          birthYear = e.birthYear,
          active = e.active,
          federationId = e.federationId
        )
        val fed = e.federationId.map: fid =>
          NewFederation(fid, Federation.all.getOrElse(fid, fid.value))
        acc.copy(crawlPlayers = (player, fed, e.hash) :: acc.crawlPlayers)

      ymOpt match
        case None =>
          warn"Batch skipped: no parseable sourceLastModified in events" *>
            eventDb.markIngested(events.map(_.id))
        case Some(ym) =>
          for
            _ <- info"Upserting ${acc.crawlPlayers.size} players for ${ym.format}"
            _ <- db.upsertCrawlPlayers(acc.crawlPlayers, ym)
            _ <- eventDb.markIngested(events.map(_.id))
            _ <- playerHashCache.update(acc.crawlPlayers.map((p, _, h) => p.fideId -> h).toMap)
          yield ()
