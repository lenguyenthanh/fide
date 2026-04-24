package fide

import cats.effect.*
import cats.syntax.all.*
import fide.broadcast.MonthlyResetHook
import fide.crawler.Crawler
import fide.db.{
  Db,
  DbResource,
  HashCache,
  Health,
  HistoryDb,
  Ingestor,
  KVStore,
  LiveRatingDb,
  LockService,
  PlayerEventDb
}
import fide.types.*
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
    val playerHashCache: HashCache[FideId],
    val liveRatingDb: LiveRatingDb,
    val lockService: LockService,
    val monthlyResetHook: MonthlyResetHook,
    val httpClient: Client[IO]
)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    for
      (res, client) <- (DbResource.instance(conf.postgres), makeClient).tupled
      db              = Db(res.postgres)
      store           = KVStore(res.postgres)
      health          = Health(res.postgres)
      eventDb         = PlayerEventDb(res.postgres)
      historyDb       = HistoryDb(res.postgres, conf.history.insertSize)
      liveRatingDb    = LiveRatingDb(res.postgres)
      lockService     = LockService(res.postgres)
      playerHashCache <- HashCache(db.allPlayerHashesByFideId).toResource
      ingestor = Ingestor(eventDb, db, playerHashCache)
      hostName         <- IO(java.net.InetAddress.getLocalHost.getHostName).toResource
      resetHolder       = s"monthly_reset@$hostName"
      monthlyResetHook  = MonthlyResetHook(liveRatingDb, lockService, resetHolder)
    yield AppResources(
      db,
      store,
      health,
      Crawler.instance(db, eventDb, store, client, conf.crawler, playerHashCache),
      historyDb,
      ingestor,
      playerHashCache,
      liveRatingDb,
      lockService,
      monthlyResetHook,
      client
    )

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
