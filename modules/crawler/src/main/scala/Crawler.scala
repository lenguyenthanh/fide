package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.{ Db, KVStore }
import fide.domain.{ Federation, NewFederation, NewPlayerHistory, NewPlayerInfo }
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
          *> upsertAllFederations
          *> downloader.fetch
            .chunkN(config.chunkSize)
            .map(_.toList)
            .parEvalMapUnordered(config.concurrentUpsert)(upsertChunk)
            .compile
            .drain
          *> info"Finished crawling"

      def upsertChunk(xs: List[(NewPlayerInfo, NewPlayerHistory)]): IO[Unit] =
        val unknownFeds = xs
          .mapFilter(_._2.federationId)
          .distinct
          .filterNot(Federation.all.contains)
          .map(id => NewFederation(id, id.value))
        db.upsertFederations(unknownFeds) *> db.upsert(xs)

      def upsertAllFederations: IO[Unit] =
        val feds = Federation.all.toList.map((id, name) => NewFederation(id, name))
        db.upsertFederations(feds) *> info"Upserted ${feds.size} federations"
