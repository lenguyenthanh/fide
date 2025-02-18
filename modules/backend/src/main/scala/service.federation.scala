package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.domain.Models.Pagination
import fide.domain.{ FederationSummary, Models }
import fide.spec.{
  BirthYear as _,
  FederationId as _,
  PageNumber as _,
  PageSize as _,
  PlayerId as _,
  Rating as _,
  *
}
import fide.types.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

class FederationServiceImpl(db: Db)(using Logger[IO]) extends FederationService[IO]:

  import FederationTransformers.*
  import PlayerTransformers.*

  override def getFederationPlayersById(
      id: FederationId,
      page: PageNumber,
      pageSize: PageSize,
      sortBy: Option[SortBy],
      order: Option[Order],
      isActive: Option[Boolean],
      standardMin: Option[Rating],
      standardMax: Option[Rating],
      rapidMin: Option[Rating],
      rapidMax: Option[Rating],
      blitzMin: Option[Rating],
      blitzMax: Option[Rating],
      name: Option[String],
      titles: Option[List[Title]],
      otherTitles: Option[List[OtherTitle]],
      gender: Option[Gender],
      birthYearMin: Option[BirthYear],
      birthYearMax: Option[BirthYear],
      hasTitle: Option[Boolean],
      hasWomenTitle: Option[Boolean],
      hasOtherTitle: Option[Boolean]
  ): IO[GetFederationPlayersByIdOutput] =

    val paging  = Models.Pagination(page, pageSize)
    val sorting = Models.Sorting.fromOption(sortBy.map(_.to[Models.SortBy]), order.map(_.to[Models.Order]))
    val filter = Models.PlayerFilter(
      name,
      isActive,
      Models.RatingRange(standardMin, standardMax),
      Models.RatingRange(rapidMin, rapidMax),
      Models.RatingRange(blitzMin, blitzMax),
      id.some,
      titles.map(_.map(_.to[domain.Title])),
      otherTitles.map(_.map(_.to[domain.OtherTitle])),
      gender.map(_.to[domain.Gender]),
      birthYearMin,
      birthYearMax,
      hasTitle,
      hasWomenTitle,
      hasOtherTitle
    )
    db.allPlayers(sorting, paging, filter)
      .handleErrorWith: e =>
        error"Error in getPlayers with $filter, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetFederationPlayersByIdOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

  override def getFederationSummaryById(id: FederationId): IO[GetFederationSummaryByIdOutput] =
    db.federationSummaryById(id)
      .handleErrorWith: e =>
        error"Error in getFederationSummaryById: $id, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(FederationNotFound(id)))(_.transform.pure)

  override def getFederationsSummary(
      page: PageNumber,
      pageSize: PageSize
  ): IO[GetFederationsSummaryOutput] =
    db.allFederationsSummary(Pagination(page, pageSize))
      .handleErrorWith: e =>
        error"Error in getFederationsSummary: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetFederationsSummaryOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

object FederationTransformers:
  extension (p: FederationSummary)
    def transform: GetFederationSummaryByIdOutput =
      p.to[GetFederationSummaryByIdOutput]
