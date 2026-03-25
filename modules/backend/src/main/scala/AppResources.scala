package fide

import cats.effect.*
import cats.syntax.all.*
import fide.crawler.Crawler
import fide.db.{ Db, DbResource, HashCache, Health, HistoryDb, Ingestor, KVStore, PlayerEventDb }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

class AppResources private (
    val db: Db,
    val store: KVStore,
    val health: Health,
    val crawler: Crawler,
    val historyDb: HistoryDb,
    val ingestor: Ingestor,
    val playerHashCache: HashCache,
    val playerInfoHashCache: HashCache
)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    for
      (res, client) <- (DbResource.instance(conf.postgres), makeClient).tupled
      db        = Db(res.postgres)
      store     = KVStore(res.postgres)
      health    = Health(res.postgres)
      eventDb   = PlayerEventDb(res.postgres)
      historyDb = HistoryDb(res.postgres)
      playerHashCache     <- HashCache(db.allPlayerHashes).toResource
      playerInfoHashCache <- HashCache(historyDb.allPlayerInfoHashes).toResource
      ingestor = Ingestor(
        eventDb,
        historyDb,
        db,
        playerHashCache,
        playerInfoHashCache
      )
    yield AppResources(
      db,
      store,
      health,
      Crawler.instance(db, eventDb, store, client, conf.crawler, playerHashCache),
      historyDb,
      ingestor,
      playerHashCache,
      playerInfoHashCache
    )

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
