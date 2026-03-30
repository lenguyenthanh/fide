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
  /** Returns true if data was crawled, false if skipped (up to date). */
  def crawl: IO[Boolean]

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

      def crawl: IO[Boolean] =
        syncer.fetchStatus.flatMap:
          case OutDated(timestamp) =>
            (fetchAndSave(timestamp) *> timestamp.traverse_(
              syncer.saveLastUpdate
            ) *> db.refreshFederationsSummary)
              .as(true)
              .handleErrorWith(e => error"Error while crawling: $e".as(false))
          case _ => info"Skipping crawling as the data is up to date".as(false)

      def fetchAndSave(timestamp: Option[String]): IO[Unit] =
        val now = OffsetDateTime.now()
        for
          _            <- info"Start crawling"
          startAt      <- IO.monotonic
          cachedHashes <- playerHashCache.get
          _            <- info"Loaded ${cachedHashes.size} player hashes for diffing"
          metrics      <- AtomicCell[IO].of(CrawlMetrics())
          seenIds      <- AtomicCell[IO].of(Set.empty[fide.types.PlayerId])
          newPlayerIds <- AtomicCell[IO].of(Set.empty[fide.types.PlayerId])
          _            <- downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert): chunk =>
              processChunk(chunk, cachedHashes, timestamp, now, metrics, seenIds, newPlayerIds, eventDb, db)
            .compile
            .drain
          elapsed <- IO.monotonic.map(_ - startAt)
          m       <- metrics.get
          seen    <- seenIds.get
          newIds  <- newPlayerIds.get
          disappeared = cachedHashes.keySet -- seen
          _ <-
            info"Crawl complete: total=${m.total}, new=${m.newPlayers}, changed=${m.changed}, unchanged=${m.unchanged}, disappeared=${disappeared.size}, duration=${elapsed.toSeconds}s"
          _ <- if newIds.nonEmpty then db.updateLastSeenAt(newIds.toList) else IO.unit
          _ <- if disappeared.nonEmpty then db.markInactive(disappeared) else IO.unit
        yield ()

      private def processChunk(
          chunk: List[(NewPlayer, Option[NewFederation], String)],
          cachedHashes: Map[fide.types.PlayerId, Long],
          timestamp: Option[String],
          now: OffsetDateTime,
          metrics: AtomicCell[IO, CrawlMetrics],
          seenIds: AtomicCell[IO, Set[fide.types.PlayerId]],
          newPlayerIds: AtomicCell[IO, Set[fide.types.PlayerId]],
          eventDb: PlayerEventDb,
          db: Db
      ): IO[Unit] =
        val players = chunk.map(_._1)
        val ids     = players.map(_.id)

        // Upsert federations (still needed, idempotent)
        val feds = chunk.mapFilter(_._2).distinctBy(_.id)

        case class ChunkResult(
            events: List[NewPlayerEvent],
            newIds: List[fide.types.PlayerId],
            newCount: Long,
            changedCount: Long,
            unchangedCount: Long
        )
        val result = players.foldLeft(ChunkResult(Nil, Nil, 0L, 0L, 0L)):
          case (acc, player) =>
            val hash = NewPlayer.computeHash(player)
            cachedHashes.get(player.id) match
              case None =>
                acc.copy(
                  events = toEvent(player, hash, now, timestamp) :: acc.events,
                  newIds = player.id :: acc.newIds,
                  newCount = acc.newCount + 1
                )
              case Some(existingHash) if existingHash != hash =>
                acc.copy(
                  events = toEvent(player, hash, now, timestamp) :: acc.events,
                  changedCount = acc.changedCount + 1
                )
              case _ =>
                acc.copy(unchangedCount = acc.unchangedCount + 1)

        for
          _ <- if feds.nonEmpty then db.upsertFederations(feds) else IO.unit
          _ <- if result.events.nonEmpty then eventDb.append(result.events) else IO.unit
          _ <- metrics.update(m =>
            m.copy(
              total = m.total + players.size,
              newPlayers = m.newPlayers + result.newCount,
              changed = m.changed + result.changedCount,
              unchanged = m.unchanged + result.unchangedCount
            )
          )
          _ <- seenIds.update(_ ++ ids)
          _ <- if result.newIds.nonEmpty then newPlayerIds.update(_ ++ result.newIds) else IO.unit
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
