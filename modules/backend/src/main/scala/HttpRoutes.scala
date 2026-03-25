package fide

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import cats.syntax.all.*
import fide.spec.*
import org.http4s.{ HttpApp, HttpRoutes }
import org.typelevel.log4cats.Logger
import smithy4s.http4s.SimpleRestJsonBuilder

def Routes(resources: AppResources)(using Logger[IO]): Resource[IO, HttpApp[IO]] =

  val playerServiceImpl: PlayerService[IO]         = PlayerServiceImpl(resources.db)
  val federationServiceImpl: FederationService[IO] = FederationServiceImpl(resources.db)
  val healthServiceImpl: HealthService[IO]         = HealthServiceImpl(resources.health)
  val historyServiceImpl: HistoryService[IO]       = HistoryServiceImpl(resources.historyDb)

  val players: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(playerServiceImpl).resource

  val health: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(healthServiceImpl).resource

  val federations: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(federationServiceImpl).resource

  val history: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(historyServiceImpl).resource

  val docs = smithy4s.http4s.swagger.docs[IO](PlayerService, FederationService, HealthService, HistoryService)
  val serviceRoutes = NonEmptyList.of(players, federations, health, history).sequence.map(_.reduceK)
  serviceRoutes
    .map(_ <+> docs)
    .map(ApplyMiddleware)
