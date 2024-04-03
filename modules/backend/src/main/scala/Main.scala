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

val stubbedServiceImpl: PlayerService[IO] = new PlayerService.Default[IO](IO.stub)
// object FideServiceImpl extends FideService[IO]:
//   def getPlayer(id: String): IO[Greeting] =
//     IO.pure(Greeting(s"Hello, $id!"))
//

object Routes:
  private val example: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(stubbedServiceImpl).resource

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](PlayerService)

  val all: Resource[IO, HttpRoutes[IO]] = example.map(_ <+> docs)

object Main extends IOApp.Simple:
  val run = Routes.all
    .flatMap: routes =>
      val thePort = port"9000"
      val theHost = host"localhost"
      val message =
        s"Server started on: $theHost:$thePort, press enter to stop"

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
