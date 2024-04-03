package fide

import fide.spec.*
import cats.syntax.all.*
import cats.effect.*
import org.http4s.implicits.*
import org.http4s.ember.server.*
import org.http4s.*
import com.comcast.ip4s.*
import smithy4s.http4s.SimpleRestJsonBuilder
import scala.concurrent.duration.*

val playerServiceImpl: PlayerService[IO] = new PlayerService.Default[IO](IO.stub)
val healthServiceImpl: HealthService[IO] = new HealthService.Default[IO](IO.stub)

object Routes:
  private val players: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(playerServiceImpl).resource

  private val health: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(healthServiceImpl).resource

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](PlayerService, HealthService)

  val serviceRoutes: Resource[IO, HttpRoutes[IO]] = (players, health).mapN(_ <+> _)
  val all: Resource[IO, HttpRoutes[IO]]           = serviceRoutes.map(_ <+> docs)

object Main extends IOApp.Simple:
  val run = Routes.all
    .flatMap: routes =>
      val thePort = port"9000"
      val theHost = host"localhost"
      val message = s"Server started on: $theHost:$thePort, press enter to stop"

      EmberServerBuilder
        .default[IO]
        .withPort(thePort)
        .withHost(theHost)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(1.second)
        .build
        .productL(IO.println(message).toResource)
    .surround(IO.readLine)
    .void
    .guarantee(IO.println("Goodbye!"))
