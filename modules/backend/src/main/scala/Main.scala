package fide

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import fide.spec.*
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import smithy4s.http4s.SimpleRestJsonBuilder

import scala.concurrent.duration.*

val playerServiceImpl: PlayerService[IO]         = new PlayerService.Default[IO](IO.stub)
val healthServiceImpl: HealthService[IO]         = new HealthService.Default[IO](IO.stub)
val federationServiceImpl: FederationService[IO] = new FederationService.Default[IO](IO.stub)

object Routes:

  private val players: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(playerServiceImpl).resource

  private val health: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(healthServiceImpl).resource

  private val federations: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(federationServiceImpl).resource

  private val docs: Resource[IO, HttpRoutes[IO]] =
    smithy4s.http4s.swagger.docs[IO](PlayerService, FederationService, HealthService).pure[Resource[IO, *]]

  val all: Resource[IO, HttpRoutes[IO]] = List(players, federations, health).fold(docs)(_ <+> _)

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
