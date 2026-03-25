package fide
package crawler

import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import fide.db.{ Db, HashCache, KVStore, PlayerEventDb }
import fide.domain.*
import io.github.arainko.ducktape.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.OffsetDateTime

import Syncer.Status.*

trait Crawler:
  def crawl: IO[Unit]

object Crawler:

  case class CrawlMetrics(
      total: Long = 0,
      newPlayers: Long = 0,
      changed: Long = 0,
      unchanged: Long = 0
  ):
    def seenIds: Long = total

  def instance(
      db: Db,
      eventDb: PlayerEventDb,
      store: KVStore,
      client: Client[IO],
      config: CrawlerConfig,
      playerHashCache: HashCache
  )(using Logger[IO]) =
    val syncer     = Syncer.instance(store, client)
    val downloader = Downloader(client)
    new Crawler:

      def crawl: IO[Unit] =
        syncer.fetchStatus.flatMap:
          case OutDated(timestamp) =>
            (fetchAndSave(timestamp) *> timestamp.traverse_(syncer.saveLastUpdate))
              .handleErrorWith(e => error"Error while crawling: $e")
          case _ => info"Skipping crawling as the data is up to date"

      def fetchAndSave(timestamp: Option[String]): IO[Unit] =
        val now = OffsetDateTime.now()
        for
          _       <- info"Start crawling"
          startAt <- IO.monotonic
          hashMap <- playerHashCache.get
          _       <- info"Loaded ${hashMap.size} player hashes for diffing"
          metrics <- AtomicCell[IO].of(CrawlMetrics())
          seenIds <- AtomicCell[IO].of(List.empty[fide.types.PlayerId])
          _       <- downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert): chunk =>
              processChunk(chunk, hashMap, timestamp, now, metrics, seenIds, eventDb, db)
            .compile
            .drain
          elapsed <- IO.monotonic.map(_ - startAt)
          m       <- metrics.get
          seen    <- seenIds.get
          disappeared = hashMap.keySet -- seen.toSet
          _ <-
            info"Crawl complete: total=${m.total}, new=${m.newPlayers}, changed=${m.changed}, unchanged=${m.unchanged}, disappeared=${disappeared.size}, duration=${elapsed.toSeconds}s"
          _ <- db.updateLastSeenAt(seen)
        yield ()

      private def processChunk(
          chunk: List[(NewPlayer, Option[NewFederation], String)],
          hashMap: Map[fide.types.PlayerId, Long],
          timestamp: Option[String],
          now: OffsetDateTime,
          metrics: AtomicCell[IO, CrawlMetrics],
          seenIds: AtomicCell[IO, List[fide.types.PlayerId]],
          eventDb: PlayerEventDb,
          db: Db
      ): IO[Unit] =
        val players = chunk.map(_._1)
        val ids     = players.map(_.id)

        // Upsert federations (still needed, idempotent)
        val feds = chunk.mapFilter(_._2).distinctBy(_.id)

        case class ChunkResult(events: List[NewPlayerEvent], newCount: Long, changedCount: Long, unchangedCount: Long)
        val result = players.foldLeft(ChunkResult(Nil, 0L, 0L, 0L)):
          case (acc, player) =>
            val hash = NewPlayer.computeHash(player)
            hashMap.get(player.id) match
              case None =>
                acc.copy(events = toEvent(player, hash, now, timestamp) :: acc.events, newCount = acc.newCount + 1)
              case Some(existingHash) if existingHash != hash =>
                acc.copy(events = toEvent(player, hash, now, timestamp) :: acc.events, changedCount = acc.changedCount + 1)
              case _ =>
                acc.copy(unchangedCount = acc.unchangedCount + 1)
        val events         = result.events
        val newCount       = result.newCount
        val changedCount   = result.changedCount
        val unchangedCount = result.unchangedCount

        for
          _ <- if feds.nonEmpty then db.upsertFederations(feds) else IO.unit
          _ <- if events.nonEmpty then eventDb.append(events) else IO.unit
          _ <- metrics.update(m =>
            m.copy(
              total = m.total + players.size,
              newPlayers = m.newPlayers + newCount,
              changed = m.changed + changedCount,
              unchanged = m.unchanged + unchangedCount
            )
          )
          _ <- seenIds.update(_ ++ ids)
        yield ()

      private def toEvent(
          player: NewPlayer,
          hash: Long,
          now: OffsetDateTime,
          timestamp: Option[String]
      ): NewPlayerEvent =
        player
          .into[NewPlayerEvent]
          .transform(
            Field.renamed(_.playerId, _.id),
            Field.const(_.hash, hash),
            Field.const(_.crawledAt, now),
            Field.const(_.sourceLastModified, timestamp)
          )
