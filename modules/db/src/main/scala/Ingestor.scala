package fide
package db

import cats.effect.*
import fide.domain.*
import fide.types.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.FiniteDuration

trait Ingestor:
  def ingest: IO[Unit]
  def purge: IO[Unit]

object Ingestor:

  private val BatchSize = 10_000

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
        _       <- info"Starting ingestion"
        startAt <- IO.monotonic
        total   <- eventDb.ungestedStream(BatchSize)
          .chunkN(BatchSize)
          .evalScan(0L): (acc, chunk) =>
            val events = chunk.toList
            info"Processing batch of ${events.size} events (total so far: $acc)" *>
              processBatch(events).as(acc + events.size)
          .compile
          .lastOrError
        elapsed <- IO.monotonic.map(_ - startAt)
        _       <- info"Ingestion complete, total=$total, duration=${elapsed.toSeconds}s"
      yield ()

    def purge: IO[Unit] =
      info"Purging events older than $ttl" *>
        eventDb.purgeOlderThan(ttl) *>
        info"Purge complete"

    private def processBatch(events: List[PlayerEvent]): IO[Unit] =
      // Group by player, take latest event per player
      val latestByPlayer = events
        .groupBy(_.playerId)
        .values
        .map(_.maxBy(_.id))
        .toList

      // Build NewPlayer + hash pairs for players upsert
      val playersWithHash: List[(NewPlayer, Long)] = latestByPlayer.map: e =>
        val player = e.into[NewPlayer].transform(Field.renamed(_.id, _.playerId))
        (player, e.hash)

      // Build player info rows with identity hash
      val playerInfoRows: List[(PlayerInfoRow, Long)] = playersWithHash.map: (player, _) =>
        val row  = player.to[PlayerInfoRow]
        val hash = NewPlayer.computeInfoHash(player)
        (row, hash)

      // Build history rows — parse sourceLastModified once
      val parsedEvents = events.map: e =>
        (e, e.sourceLastModified.flatMap(YearMonth.fromLastModified))

      val historyRows = parsedEvents
        .collect:
          case (e, Some(ym)) =>
            e.into[PlayerHistoryRow].transform(Field.const(_.yearMonth, ym))
        .groupBy(r => (r.playerId, r.yearMonth))
        .values
        .map(_.last)
        .toList

      val skippedCount = parsedEvents.count(_._2.isEmpty)

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
        // Upsert players with hash
        _ <- db.upsertPlayersWithHash(playersWithHash)
        // Upsert only changed player_info rows
        _ <- if changedInfoRows.nonEmpty then historyDb.upsertPlayerInfoWithHash(changedInfoRows) else IO.unit
        _ <- historyDb.upsertPlayerHistory(historyRows)
        _ <- eventDb.markIngested(events.map(_.id))
        // Update caches
        _ <- playerHashCache.update(playersWithHash.map((p, h) => p.id -> h).toMap)
        _ <- playerInfoHashCache.update(changedInfoRows.map((r, h) => r.id -> h).toMap)
      yield ()
