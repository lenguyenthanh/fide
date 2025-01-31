package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.{ Db, KVStore }
import fide.domain.*
import org.http4s.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import Syncer.Status.*

trait Crawler:
  def crawl: IO[Unit]

object Crawler:

  def instance(db: Db, store: KVStore, client: Client[IO], config: CrawlerConfig)(using Logger[IO]) =
    val syncer     = Syncer.instance(store, client)
    val downloader = Downloader(client)
    new Crawler:

      def crawl: IO[Unit] =
        syncer.fetchStatus.flatMap:
          case OutDated(timestamp) =>
            (fetchAndSave *> timestamp.traverse_(syncer.saveLastUpdate))
              .handleErrorWith(e => error"Error while crawling: $e")
          case _ => info"Skipping crawling as the data is up to date"

      def fetchAndSave: IO[Unit] =
        info"Start crawling"
          *> downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert)(db.upsert)
            .compile
            .drain
          *> info"Finished crawling"
