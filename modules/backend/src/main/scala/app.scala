package fide

import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = app.useForever

  def app: Resource[IO, Unit] =
    for
      config <- AppConfig.load.toResource
      _      <- Logger[IO].info(s"Starting fide-api with config: $config").toResource
      res    <- AppResources.instance(config.postgres)
      _      <- FideApp(res, config).run()
    yield ()

class FideApp(res: AppResources, config: AppConfig)(using Logger[IO]):
  def run(): Resource[IO, Unit] =
    for
      httpApp <- Routes(res)
      server  <- MkHttpServer.apply.newEmber(config.server, httpApp)
      _       <- Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}").toResource
    yield ()
