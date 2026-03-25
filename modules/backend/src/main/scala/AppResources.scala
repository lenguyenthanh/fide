package fide

import cats.effect.*
import cats.syntax.all.*
import fide.crawler.Crawler
import fide.db.{ Db, DbResource, Health, HistoryDb, Ingestor, KVStore, PlayerEventDb }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

class AppResources private (
    val db: Db,
    val store: KVStore,
    val health: Health,
    val crawler: Crawler,
    val historyDb: HistoryDb,
    val ingestor: Ingestor
)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    (DbResource.instance(conf.postgres), makeClient).mapN: (res, client) =>
      val db        = Db(res.postgres)
      val store     = KVStore(res.postgres)
      val health    = Health(res.postgres)
      val eventDb   = PlayerEventDb(res.postgres)
      val historyDb = HistoryDb(res.postgres)
      val ingestor  = Ingestor(eventDb, historyDb, conf.ingestion.eventTtlDays.toLong.days)
      AppResources(
        db,
        store,
        health,
        Crawler.instance(db, eventDb, store, client, conf.crawler),
        historyDb,
        ingestor
      )

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
