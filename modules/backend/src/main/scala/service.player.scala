package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.domain.Models
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
import smithy4s.Timestamp

import java.time.OffsetDateTime

class PlayerServiceImpl(db: Db)(using Logger[IO]) extends PlayerService[IO]:

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
    val filter = Models.PlayerFilter(
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

  override def getPlayerById(id: PlayerId): IO[GetPlayerByIdOutput] =
    db.playerById(id)
      .handleErrorWith: e =>
        error"Error in getPlayerById: $id, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id))):
          _.transform.pure[IO]

  override def getPlayerByIds(ids: NonEmptySet[PlayerId]): IO[GetPlayerByIdsOutput] =
    db.playersByIds(ids.value)
      .handleErrorWith: e =>
        error"Error in getPlayersByIds: $ids, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(p => p.id.toString -> p.transform).toMap)
      .map(GetPlayerByIdsOutput.apply)

  override def getPlayerRatingHistory(
      id: PlayerId,
      limit: Option[Int],
      page: Option[Int]
  ): IO[GetPlayerRatingHistoryOutput] =
    // First check if player exists
    db.playerById(id)
      .flatMap:
        case None => IO.raiseError(PlayerNotFound(id))
        case Some(_) =>
          val pageSize = limit.getOrElse(100)
          val pageNum  = page.getOrElse(1)
          val offset   = (pageNum - 1) * pageSize

          (
            db.ratingHistoryForPlayer(id, Some(pageSize), Some(offset)),
            db.countRatingHistoryForPlayer(id)
          ).parMapN: (history, totalCount) =>
            val nextPage = Option.when(offset + pageSize < totalCount)(pageNum + 1)
            GetPlayerRatingHistoryOutput(id, history.map(_.transform), totalCount, nextPage)
          .handleErrorWith: e =>
              error"Error in getPlayerRatingHistory: $id, $e" *>
                IO.raiseError(InternalServerError("Internal server error"))

  override def getPlayersRatingsByMonth(
      year: Int,
      month: Int,
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
  ): IO[GetPlayersRatingsByMonthOutput] =
    val pageNum = page.toInt
    val pageSz  = pageSize
    val offset  = (pageNum - 1) * pageSz
    // Convert year/month to epoch-based month index
    val monthIndex = (year - 1970) * 12 + (month - 1)

    (
      db.ratingHistoryForMonth(monthIndex, Some(pageSz), Some(offset)),
      db.countRatingHistoryForMonth(monthIndex)
    ).parMapN: (ratingsWithPlayers, totalCount) =>
      val ratings = ratingsWithPlayers.map: (rating, player) =>
        PlayerMonthlyRating(
          playerId = rating.playerId,
          playerName = player.name,
          standard = rating.standard,
          standardK = rating.standardK,
          rapid = rating.rapid,
          rapidK = rating.rapidK,
          blitz = rating.blitz,
          blitzK = rating.blitzK
        )
      GetPlayersRatingsByMonthOutput(
        year,
        month,
        ratings,
        totalCount,
        Option.when(ratings.size == pageSize)(page.succ)
      )
    .handleErrorWith: e =>
        error"Error in getPlayersRatingsByMonth: $year/$month, $e" *>
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
          )
        )

  extension (r: fide.domain.RatingHistoryEntry)
    def transform: RatingHistoryEntryOutput =
      r.into[RatingHistoryEntryOutput]
        .transform(
          Field.computed(_.year, entry => Some(entry.year)),
          Field.const(_.month, r.month)
        )
