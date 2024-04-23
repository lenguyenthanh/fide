package fide

import cats.effect.*
import fide.db.Db
import fide.spec.*
import fide.types.Natural
import org.typelevel.log4cats.Logger

class FederationServiceImpl(db: Db)(using Logger[IO]) extends FederationService[IO]:

  override def getFederationSummaryById(id: FederationId): IO[FederationSummary] = ???

  override def getFederationPlayersById(
      id: FederationId,
      page: Natural,
      pageSize: Natural
  ): IO[GetFederationPlayersByIdOutput] = ???

  override def getFederationsSummary(
      page: Natural,
      pageSize: Natural
  ): IO[GetFederationsSummaryOutput] = ???
