package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.{ Db, KVStore, PlayerEventDb }
import fide.domain.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import java.time.OffsetDateTime

import Syncer.Status.*

trait Crawler:
  def crawl: IO[Unit]

object Crawler:

  def instance(db: Db, eventDb: PlayerEventDb, store: KVStore, client: Client[IO], config: CrawlerConfig)(
      using Logger[IO]
  ) =
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
        info"Start crawling"
          *> downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert): chunk =>
              val dbData = chunk.map((p, f, _) => (p, f))
              val events = chunk.map: (player, _, _) =>
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
                  hash = NewPlayer.computeHash(player),
                  crawledAt = now,
                  sourceLastModified = timestamp
                )
              db.upsert(dbData) *> eventDb.append(events)
            .compile
            .drain
          *> info"Finished crawling"
