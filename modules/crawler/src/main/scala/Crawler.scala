package fide
package crawler

import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import fide.db.{ Db, HashCache, KVStore, PlayerEventDb }
import fide.domain.*
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
          _        <- info"Start crawling"
          startAt  <- IO.monotonic
          hashMap  <- playerHashCache.get
          _        <- info"Loaded ${hashMap.size} player hashes for diffing"
          metrics  <- AtomicCell[IO].of(CrawlMetrics())
          seenIds  <- AtomicCell[IO].of(List.empty[fide.types.PlayerId])
          _ <- downloader.fetch
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
          _ <- info"Crawl complete: total=${m.total}, new=${m.newPlayers}, changed=${m.changed}, unchanged=${m.unchanged}, disappeared=${disappeared.size}, duration=${elapsed.toSeconds}s"
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

        var newCount     = 0L
        var changedCount = 0L
        var unchangedCount = 0L

        val events = players.mapFilter: player =>
          val hash = NewPlayer.computeHash(player)
          hashMap.get(player.id) match
            case None =>
              newCount += 1
              Some(toEvent(player, hash, now, timestamp))
            case Some(existingHash) if existingHash != hash =>
              changedCount += 1
              Some(toEvent(player, hash, now, timestamp))
            case _ =>
              unchangedCount += 1
              None

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
        NewPlayerEvent(
          playerId = player.id,
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
