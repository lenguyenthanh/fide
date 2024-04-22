package fide

import cats.effect.*
import fide.db.Db
import fide.spec.*
import org.typelevel.log4cats.Logger

class FederationServiceImpl(db: Db)(using Logger[IO]) extends FederationService[IO]:

  override def getFederationSummaryById(id: FederationId): IO[FederationSummary] = ???

  override def getFederationPlayersById(
      id: FederationId,
      page: Option[PageNumber],
      pageSize: Option[Int]
  ): IO[GetFederationPlayersByIdOutput] = ???

  override def getFederationsSummary(
      page: Option[PageNumber],
      pageSize: Option[Int]
  ): IO[GetFederationsSummaryOutput] = ???
