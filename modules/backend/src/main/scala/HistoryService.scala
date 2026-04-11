package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.HistoryDb
import fide.domain.{ HistoricalPlayerInfo, Models }
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

class HistoryServiceImpl(historyDb: HistoryDb)(using Logger[IO]) extends HistoryService[IO]:

  import HistoryTransformers.*
  import FederationTransformers.*

  override def getHistoricalPlayers(
      month: YearMonth,
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
      hasOtherTitle: Option[Boolean],
      federationId: Option[FederationId]
  ): IO[GetHistoricalPlayersOutput] =
    val paging  = Models.Pagination(page, pageSize)
    val sorting = Models.Sorting.fromOption(sortBy.map(_.to[Models.SortBy]), order.map(_.to[Models.Order]))
    val filter  = Models.PlayerFilter(
      name,
      isActive,
      Models.RatingRange(standardMin, standardMax),
      Models.RatingRange(rapidMin, rapidMax),
      Models.RatingRange(blitzMin, blitzMax),
      federationId,
      titles.map(_.map(_.to[domain.Title])),
      otherTitles.map(_.map(_.to[domain.OtherTitle])),
      gender.map(_.to[domain.Gender]),
      birthYearMin,
      birthYearMax,
      hasTitle,
      hasWomenTitle,
      hasOtherTitle
    )
    (
      historyDb.allPlayers(month, sorting, paging, filter).map(_.map(_.transform)),
      historyDb.countPlayers(month, filter)
    )
      .parMapN: (xs, total) =>
        GetHistoricalPlayersOutput(xs, total, Option.when(xs.size == pageSize)(page.succ))
      .handleErrorWith: e =>
        error"Error in getHistoricalPlayers: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))

  override def getHistoricalPlayerById(id: PlayerId, month: YearMonth): IO[GetHistoricalPlayerByIdOutput] =
    historyDb
      .playerById(id, month)
      .handleErrorWith: e =>
        error"Error in getHistoricalPlayerById: $id, $month, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id)))(_.transform.pure[IO])

  override def getHistoricalFederationsSummary(
      month: YearMonth,
      page: PageNumber,
      pageSize: PageSize
  ): IO[GetHistoricalFederationsSummaryOutput] =
    (
      historyDb.federationsSummary(month, Models.Pagination(page, pageSize)).map(_.map(_.transform)),
      historyDb.countFederationsSummary(month)
    ).parMapN: (xs, total) =>
      GetHistoricalFederationsSummaryOutput(xs, total, Option.when(xs.size == pageSize)(page.succ))
    .handleErrorWith: e =>
        error"Error in getHistoricalFederationsSummary: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))

  override def getHistoricalFederationSummaryById(
      id: FederationId,
      month: YearMonth
  ): IO[GetFederationSummaryByIdOutput] =
    historyDb
      .federationSummaryById(id, month)
      .handleErrorWith: e =>
        error"Error in getHistoricalFederationSummaryById: $id, $month, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(FederationNotFound(id)))(_.transform.pure[IO])

  override def getAvailableMonths(): IO[GetAvailableMonthsOutput] =
    historyDb.availableMonths
      .map(months => GetAvailableMonthsOutput(months))
      .handleErrorWith: e =>
        error"Error in getAvailableMonths: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))

object HistoryTransformers:

  extension (p: HistoricalPlayerInfo)
    def transform: GetHistoricalPlayerByIdOutput =
      p.into[GetHistoricalPlayerByIdOutput]
        .transform(
          Field.const(_.month, p.yearMonth),
          Field.const(_.fideId, p.fideId),
          Field.const(
            _.otherTitles,
            if p.otherTitles.isEmpty then none else p.otherTitles.map(_.to[OtherTitle]).some
          )
        )
