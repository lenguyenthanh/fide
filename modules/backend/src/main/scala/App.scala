package fide

import cats.effect.*
import cats.effect.std.Random
import fide.broadcast.{ LichessClient, LiveRatingIngester, LiveRatingJob, LiveRatingSyncer }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.*

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = app.useForever

  def app: Resource[IO, Unit] =
    for
      config <- AppConfig.load.toResource
      _      <- Logger[IO].info(s"Starting fide-api with config: $config").toResource
      res    <- AppResources.instance(config)
      _      <- FideApp(res, config).run()
    yield ()

class FideApp(res: AppResources, config: AppConfig)(using Logger[IO]):
  def run(): Resource[IO, Unit] =
    for
      httpApp <- Routes(res)
      server  <- MkHttpServer.apply.newEmber(config.server, httpApp)
      _       <- CrawlerJob(res.crawler, res.ingestor, config.crawlerJob, res.monthlyResetHook).run()
      _       <- liveRatingJob.run()
      _       <- Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}").toResource
    yield ()

  private lazy val liveRatingJob: LiveRatingJob =
    import LichessClient.given // Raise[IO, LichessError]
    given Random[IO] = Random.javaUtilConcurrentThreadLocalRandom[IO]
    val hostName     = java.net.InetAddress.getLocalHost.getHostName
    val tickHolder   = s"tick@$hostName"
    val client       = LichessClient(res.httpClient, config.lichess)
    val ingester     = LiveRatingIngester(
      client,
      ids => res.db.playersByFideIds(ids),
      res.liveRatingDb
    )
    val syncer = LiveRatingSyncer(
      client,
      ingester,
      res.liveRatingDb,
      res.lockService,
      tickHolder,
      config.lichess.maxConcurrentRounds
    )
    LiveRatingJob(
      syncer,
      delay = config.liveRatingJob.delayInSeconds.seconds,
      pollInterval = config.liveRatingJob.pollIntervalInMinutes.minutes
    )
