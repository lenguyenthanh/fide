package fide
package db

import cats.effect.*
import fide.domain.*
import fide.types.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.FiniteDuration

trait Ingestor:
  def ingest: IO[Unit]
  def purge: IO[Unit]

object Ingestor:

  def apply(
      eventDb: PlayerEventDb,
      historyDb: HistoryDb,
      ttl: FiniteDuration
  )(using Logger[IO]): Ingestor = new:

    def ingest: IO[Unit] =
      for
        _      <- info"Starting ingestion"
        events <- eventDb.uningested()
        _      <- info"Found ${events.size} uningested events"
        _      <- if events.isEmpty then IO.unit else processEvents(events)
        _      <- info"Ingestion complete"
      yield ()

    def purge: IO[Unit] =
      info"Purging events older than $ttl" *>
        eventDb.purgeOlderThan(ttl) *>
        info"Purge complete"

    private def processEvents(events: List[PlayerEvent]): IO[Unit] =
      val playerInfoRows = events
        .groupBy(_.playerId)
        .values
        .map(_.maxBy(_.id))
        .map(e => PlayerInfoRow(e.playerId, e.name, e.gender, e.birthYear))
        .toList

      val historyRows = events
        .flatMap: e =>
          e.sourceLastModified
            .flatMap(YearMonth.fromLastModified)
            .map: ym =>
              PlayerHistoryRow(
                playerId = e.playerId,
                yearMonth = ym,
                title = e.title,
                womenTitle = e.womenTitle,
                otherTitles = e.otherTitles,
                standard = e.standard,
                standardK = e.standardK,
                rapid = e.rapid,
                rapidK = e.rapidK,
                blitz = e.blitz,
                blitzK = e.blitzK,
                federationId = e.federationId,
                active = e.active
              )
        .groupBy(r => (r.playerId, r.yearMonth))
        .values
        .map(_.last)
        .toList

      val skippedCount = events.count(_.sourceLastModified.flatMap(YearMonth.fromLastModified).isEmpty)

      for
        _ <-
          if skippedCount > 0 then
            warn"$skippedCount events skipped for history (unparseable sourceLastModified)"
          else IO.unit
        _ <- info"Upserting ${playerInfoRows.size} player info rows and ${historyRows.size} history rows"
        _ <- historyDb.upsertPlayerInfo(playerInfoRows)
        _ <- historyDb.upsertPlayerHistory(historyRows)
        _ <- eventDb.markIngested(events.map(_.id))
      yield ()
