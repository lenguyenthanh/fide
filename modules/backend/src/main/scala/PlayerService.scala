package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.{ Db, HistoryDb }
import fide.domain.Models
import fide.spec.{
  BirthYear as _,
  FederationId as _,
  FideId as _,
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
import smithy4s.time.Timestamp

import java.time.OffsetDateTime

class PlayerServiceImpl(db: Db, historyDb: HistoryDb)(using Logger[IO]) extends PlayerService[IO]:

  import PlayerTransformers.*

  override def getPlayers(
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
  ): IO[GetPlayersOutput] =

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
    (db.allPlayers(sorting, paging, filter).map(_.map(_.transform)), db.countPlayers(filter))
      .parMapN: (xs, total) =>
        GetPlayersOutput(xs, total, Option.when(xs.size == pageSize)(page.succ))
      .handleErrorWith: e =>
        error"Error in getPlayers with $filter, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))

  override def getPlayerByFideId(fideId: FideId): IO[GetPlayerByIdOutput] =
    db.playerByFideId(fideId)
      .handleErrorWith: e =>
        error"Error in getPlayerByFideId: $fideId, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(PlayerFideIdNotFound(fideId))):
          _.transform.pure[IO]

  override def getPlayerByFideIds(ids: NonEmptySet[FideId]): IO[GetPlayerByFideIdsOutput] =
    db.playersByFideIds(ids.value)
      .handleErrorWith: e =>
        error"Error in getPlayerByFideIds: $ids, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(p => p.fideId.fold(p.id.toString)(_.toString) -> p.transform).toMap)
      .map(GetPlayerByFideIdsOutput.apply)

  override def getPlayerHistoryByFideId(
      fideId: FideId,
      page: PageNumber,
      pageSize: PageSize,
      since: Option[YearMonth],
      until: Option[YearMonth]
  ): IO[GetPlayerHistoryByFideIdOutput] =
    val paging = Models.Pagination(page, pageSize)
    historyDb
      .playerInfoExistsByFideId(fideId)
      .flatMap:
        if _ then
          historyDb
            .playerRatingHistoryByFideId(fideId, since, until, paging)
            .map: entries =>
              val items = entries.map: e =>
                RatingHistoryEntry(e.yearMonth, e.standard, e.rapid, e.blitz)
              GetPlayerHistoryByFideIdOutput(items, Option.when(items.size == pageSize)(page.succ))
        else IO.raiseError(PlayerFideIdNotFound(fideId))
      .handleErrorWith:
        case e: PlayerFideIdNotFound => IO.raiseError(e)
        case e                       =>
          error"Error in getPlayerHistoryByFideId: $fideId, $e" *>
            IO.raiseError(InternalServerError("Internal server error"))

  override def getPlayerByInternalId(id: PlayerId): IO[GetPlayerByIdOutput] =
    db.playerById(id)
      .handleErrorWith: e =>
        error"Error in getPlayerByInternalId: $id, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id))):
          _.transform.pure[IO]

  override def getPlayerByInternalIds(ids: NonEmptySet[PlayerId]): IO[GetPlayerByInternalIdsOutput] =
    db.playersByIds(ids.value)
      .handleErrorWith: e =>
        error"Error in getPlayerByInternalIds: $ids, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(p => p.id.toString -> p.transform).toMap)
      .map(GetPlayerByInternalIdsOutput.apply)

  override def getPlayerHistoryByInternalId(
      id: PlayerId,
      page: PageNumber,
      pageSize: PageSize,
      since: Option[YearMonth],
      until: Option[YearMonth]
  ): IO[GetPlayerHistoryByInternalIdOutput] =
    val paging = Models.Pagination(page, pageSize)
    historyDb
      .playerInfoExists(id)
      .flatMap:
        if _ then
          historyDb
            .playerRatingHistory(id, since, until, paging)
            .map: entries =>
              val items = entries.map: e =>
                RatingHistoryEntry(e.yearMonth, e.standard, e.rapid, e.blitz)
              GetPlayerHistoryByInternalIdOutput(items, Option.when(items.size == pageSize)(page.succ))
        else IO.raiseError(PlayerNotFound(id))
      .handleErrorWith:
        case e: PlayerNotFound => IO.raiseError(e)
        case e                 =>
          error"Error in getPlayerHistoryByInternalId: $id, $e" *>
            IO.raiseError(InternalServerError("Internal server error"))

object PlayerTransformers:
  given Transformer.Derived[OffsetDateTime, Timestamp] =
    Transformer.Derived.FromFunction(x => Timestamp.fromEpochSecond(x.toEpochSecond()))

  extension (p: fide.domain.PlayerInfo)
    def transform: GetPlayerByIdOutput =
      p.into[GetPlayerByIdOutput]
        .transform(
          Field.const(
            _.otherTitles,
            if p.otherTitles.isEmpty then none else p.otherTitles.map(_.to[OtherTitle]).some
          ),
          Field.const(_.fideId, p.fideId)
        )
