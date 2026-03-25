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
      db: Db,
      playerHashCache: HashCache,
      playerInfoHashCache: HashCache,
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
      // Group by player, take latest event per player
      val latestByPlayer = events
        .groupBy(_.playerId)
        .values
        .map(_.maxBy(_.id))
        .toList

      // Build NewPlayer + hash pairs for players upsert
      val playersWithHash: List[(NewPlayer, Long)] = latestByPlayer.map: e =>
        val player = NewPlayer(
          id = e.playerId,
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
        (player, e.hash)

      // Build player info rows with identity hash
      val playerInfoRows: List[(PlayerInfoRow, Long)] = latestByPlayer.map: e =>
        val row = PlayerInfoRow(e.playerId, e.name, e.gender, e.birthYear)
        val hash = NewPlayer.computeInfoHash(NewPlayer(
          id = e.playerId,
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
        ))
        (row, hash)

      // Build history rows
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
        // Diff player_info: only upsert rows whose identity hash changed
        infoHashMap        <- playerInfoHashCache.get
        changedInfoRows     = playerInfoRows.filter((row, hash) => infoHashMap.get(row.id).forall(_ != hash))
        playerInfoUpdated   = changedInfoRows.size
        _ <- info"Upserting ${playersWithHash.size} players, $playerInfoUpdated player info rows, and ${historyRows.size} history rows"
        // Upsert players with hash (moved from crawl phase)
        _ <- db.upsertPlayersWithHash(playersWithHash)
        // Upsert only changed player_info rows
        _ <- if changedInfoRows.nonEmpty then historyDb.upsertPlayerInfoWithHash(changedInfoRows) else IO.unit
        _ <- historyDb.upsertPlayerHistory(historyRows)
        _ <- eventDb.markIngested(events.map(_.id))
        // Update caches
        _ <- playerHashCache.update(playersWithHash.map((p, h) => p.id -> h).toMap)
        _ <- playerInfoHashCache.update(changedInfoRows.map((r, h) => r.id -> h).toMap)
      yield ()
