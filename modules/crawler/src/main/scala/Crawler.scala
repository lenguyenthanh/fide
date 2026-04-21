package fide
package crawler

import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import fide.db.{ Db, HashCache, KVStore, PlayerEventDb }
import fide.domain.*
import fide.types.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.OffsetDateTime

import Syncer.Status.*

trait Crawler:
  def crawl: IO[Crawler.CrawlStatus]

object Crawler:

  enum CrawlStatus:
    case Done, Skipped

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
      playerHashCache: HashCache[FideId]
  )(using Logger[IO]) =
    val syncer     = Syncer.instance(store, client)
    val downloader = Downloader(client)
    new Crawler:

      def crawl: IO[CrawlStatus] =
        syncer.fetchStatus.flatMap:
          case OutDated(timestamp) =>
            (fetchAndSave(timestamp) *> timestamp.traverse_(
              syncer.saveLastUpdate
            ) *> db.refreshFederationsSummary)
              .as(CrawlStatus.Done)
              .handleErrorWith(e => error"Error while crawling: $e".as(CrawlStatus.Skipped))
          case _ => info"Skipping crawling as the data is up to date".as(CrawlStatus.Skipped)

      def fetchAndSave(timestamp: Option[String]): IO[Unit] =
        for
          now          <- IO.realTimeZonedDateTime.map(_.toOffsetDateTime)
          _            <- info"Start crawling"
          startAt      <- IO.monotonic
          cachedHashes <- playerHashCache.get
          _            <- info"Loaded ${cachedHashes.size} player hashes for diffing"
          metrics      <- AtomicCell[IO].of(CrawlMetrics())
          seenFideIds  <- AtomicCell[IO].of(Set.empty[FideId])
          newFideIds   <- AtomicCell[IO].of(Set.empty[FideId])
          _            <- downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert): chunk =>
              processChunk(chunk, cachedHashes, timestamp, now, metrics, seenFideIds, newFideIds, eventDb, db)
            .compile
            .drain
          elapsed <- IO.monotonic.map(_ - startAt)
          m       <- metrics.get
          seen    <- seenFideIds.get
          disappeared = cachedHashes.keySet -- seen
          _ <-
            info"Crawl complete: total=${m.total}, new=${m.newPlayers}, changed=${m.changed}, unchanged=${m.unchanged}, disappeared=${disappeared.size}, duration=${elapsed.toSeconds}s"
        // TODO: updateLastSeenAt and markInactive need to resolve FideId -> PlayerId
        yield ()

      private def processChunk(
          chunk: List[(CrawlPlayer, Option[NewFederation], String)],
          cachedHashes: Map[FideId, Long],
          timestamp: Option[String],
          now: OffsetDateTime,
          metrics: AtomicCell[IO, CrawlMetrics],
          seenFideIds: AtomicCell[IO, Set[FideId]],
          newFideIds: AtomicCell[IO, Set[FideId]],
          eventDb: PlayerEventDb,
          db: Db
      ): IO[Unit] =
        val players = chunk.map(_._1)
        val fideIds = players.map(_.fideId)

        // Upsert federations (still needed, idempotent)
        val feds = chunk.mapFilter(_._2).distinctBy(_.id)

        case class ChunkResult(
            events: List[NewPlayerEvent],
            newFideIds: List[FideId],
            newCount: Long,
            changedCount: Long,
            unchangedCount: Long
        )
        val result = players.foldLeft(ChunkResult(Nil, Nil, 0L, 0L, 0L)):
          case (acc, player) =>
            val hash = CrawlPlayer.computeHash(player)
            cachedHashes.get(player.fideId) match
              case None =>
                acc.copy(
                  events = toEvent(player, hash, now, timestamp) :: acc.events,
                  newFideIds = player.fideId :: acc.newFideIds,
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
          _ <- seenFideIds.update(_ ++ fideIds)
          _ <- if result.newFideIds.nonEmpty then newFideIds.update(_ ++ result.newFideIds) else IO.unit
        yield ()

      private def toEvent(
          player: CrawlPlayer,
          hash: Long,
          now: OffsetDateTime,
          timestamp: Option[String]
      ): NewPlayerEvent =
        NewPlayerEvent(
          fideId = player.fideId,
          name = player.name,
          title = player.title,
          womenTitle = player.womenTitle,
          otherTitles = player.otherTitles,
          standard = player.standard,
          standardK = player.standardK,
          rapid = player.rapid,
          rapidK = player.rapidK,
          blitz = player.blitz,
          blitzK = player.blitzK,
          gender = player.gender,
          birthYear = player.birthYear,
          active = player.active,
          federationId = player.federationId,
          hash = hash,
          crawledAt = now,
          sourceLastModified = timestamp
        )
