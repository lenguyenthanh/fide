package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.domain.Models
import fide.spec.{ FederationId as _, PageNumber as _, PageSize as _, PlayerId as _, Rating as _, * }
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
      name: Option[String]
  ): IO[GetPlayersOutput] =
    val paging  = Models.Pagination(page, pageSize)
    val sorting = Models.Sorting.fromOption(sortBy.map(_.to[Models.SortBy]), order.map(_.to[Models.Order]))
    val filter = Models.PlayerFilter(
      name,
      isActive,
      Models.RatingRange(standardMin, standardMax),
      Models.RatingRange(rapidMin, rapidMax),
      Models.RatingRange(blitzMin, blitzMax),
      None
    )
    db.allPlayers(sorting, paging, filter)
      .handleErrorWith: e =>
        error"Error in getPlayers with $filter, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetPlayersOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

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

object PlayerTransformers:
  given Transformer.Derived[OffsetDateTime, Timestamp] =
    Transformer.Derived.FromFunction(Timestamp.fromOffsetDateTime)

  extension (p: fide.domain.PlayerInfo)
    def transform: GetPlayerByIdOutput =
      p.into[GetPlayerByIdOutput]
        .transform(
          Field.const(
            _.otherTitles,
            if p.otherTitles.isEmpty then none else p.otherTitles.map(_.to[OtherTitle]).some
          )
        )
