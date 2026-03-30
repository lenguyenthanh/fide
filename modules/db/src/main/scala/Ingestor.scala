package fide
package db

import cats.effect.*
import fide.domain.*
import fide.types.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait Ingestor:
  def ingest: IO[Unit]

object Ingestor:

  private val BatchSize = 10_000

  def apply(
      eventDb: PlayerEventDb,
      historyDb: HistoryDb,
      db: Db,
      playerHashCache: HashCache,
      playerInfoHashCache: HashCache
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
          playersWithHash: List[(NewPlayer, Long)],
          playerInfoRows: List[(PlayerInfoRow, Long)],
          historyByKey: Map[(PlayerId, YearMonth), PlayerHistoryRow],
          skippedCount: Int
      )

      val acc = events.foldLeft(Acc(Nil, Nil, Map.empty, 0)): (acc, e) =>
        val player   = e.into[NewPlayer].transform(Field.renamed(_.id, _.playerId))
        val infoRow  = player.to[PlayerInfoRow]
        val infoHash = NewPlayer.computeInfoHash(player)
        val ymOpt    = e.sourceLastModified.flatMap(YearMonth.fromLastModified)
        val newHistory = ymOpt match
          case Some(ym) =>
            val row = e.into[PlayerHistoryRow].transform(Field.const(_.yearMonth, ym))
            acc.historyByKey.updated((row.playerId, ym), row)
          case None => acc.historyByKey
        acc.copy(
          playersWithHash = (player, e.hash) :: acc.playersWithHash,
          playerInfoRows = (infoRow, infoHash) :: acc.playerInfoRows,
          historyByKey = newHistory,
          skippedCount = acc.skippedCount + (if ymOpt.isEmpty then 1 else 0)
        )

      val historyRows = acc.historyByKey.values.toList

      for
        _ <-
          if acc.skippedCount > 0 then
            warn"${acc.skippedCount} events skipped for history (unparseable sourceLastModified)"
          else IO.unit
        // Diff player_info: only upsert rows whose identity hash changed
        infoHashMap <- playerInfoHashCache.get
        changedInfoRows   = acc.playerInfoRows.filter((row, hash) => infoHashMap.get(row.id).forall(_ != hash))
        playerInfoUpdated = changedInfoRows.size
        _ <-
          info"Upserting ${acc.playersWithHash.size} players, $playerInfoUpdated player info rows, and ${historyRows.size} history rows"
        _ <- db.upsertPlayersWithHash(acc.playersWithHash)
        _ <- if changedInfoRows.nonEmpty then historyDb.upsertPlayerInfoWithHash(changedInfoRows) else IO.unit
        _ <- historyDb.upsertPlayerHistory(historyRows)
        _ <- eventDb.markIngested(events.map(_.id))
        _ <- playerHashCache.update(acc.playersWithHash.map((p, h) => p.id -> h).toMap)
        _ <- playerInfoHashCache.update(changedInfoRows.map((r, h) => r.id -> h).toMap)
      yield ()
