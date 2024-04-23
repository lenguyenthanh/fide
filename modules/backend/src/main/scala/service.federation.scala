package fide

import cats.effect.*
import fide.db.Db
import fide.domain.Models.Pagination
import fide.spec.*
import fide.types.Natural
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

class FederationServiceImpl(db: Db)(using Logger[IO]) extends FederationService[IO]:

  import FederationTransformers.*
  override def getFederationSummaryById(id: FederationId): IO[FederationSummary] =
    IO.raiseError(InternalServerError("Not implemented yet"))

  override def getFederationPlayersById(
      id: FederationId,
      page: Natural,
      pageSize: Natural
  ): IO[GetFederationPlayersByIdOutput] =
    IO.raiseError(InternalServerError("Not implemented yet"))

  override def getFederationsSummary(
      page: Natural,
      pageSize: Natural
  ): IO[GetFederationsSummaryOutput] =
    db.allFederationsSummary(Pagination.fromPageAndSize(page, pageSize))
      .handleErrorWith: e =>
        error"Error in getFederationsSummary: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetFederationsSummaryOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

private object FederationTransformers:
  given Transformer.Derived[String, FederationId] = Transformer.Derived.FromFunction(FederationId.apply)
  extension (p: fide.domain.FederationSummary)
    def transform: FederationSummary =
      p.to[FederationSummary]
