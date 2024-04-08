package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.domain.Models
import fide.spec.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import smithy4s.Timestamp

import java.time.OffsetDateTime

import Transformers.given

class PlayerServiceImpl(db: Db)(using Logger[IO]) extends PlayerService[IO]:

  override def getPlayers(
      sortBy: Option[SortBy],
      order: Option[Order],
      isActive: Option[Boolean],
      standardMin: Option[Rating],
      standardMax: Option[Rating],
      rapidMin: Option[Rating],
      rapidMax: Option[Rating],
      blitzMin: Option[Rating],
      blitzMax: Option[Rating],
      query: Option[String],
      page: Option[String],
      size: Option[Int]
  ): IO[GetPlayersOutput] =
    val _size   = size.getOrElse(Models.Pagination.defaultLimit)
    val _page   = page.flatMap(_.toIntOption).getOrElse(Models.Pagination.defaultPage)
    val paging  = Models.Pagination.fromPageAndSize(_page, _size)
    val _order  = order.map(_.to[Models.Order]).getOrElse(Models.Order.Desc)
    val _sortBy = sortBy.map(_.to[Models.SortBy]).getOrElse(Models.SortBy.Name)
    val sorting = Models.Sorting(_sortBy, _order)
    val filter = Models.Filter(
      isActive.getOrElse(true),
      Models.RatingRange(standardMin.map(_.value), standardMax.map(_.value)),
      Models.RatingRange(rapidMin.map(_.value), rapidMax.map(_.value)),
      Models.RatingRange(blitzMin.map(_.value), blitzMax.map(_.value))
    )
    query
      .fold(db.allPlayers(sorting, paging, filter))(db.playersByName(_, sorting, paging, filter))
      .handleErrorWith: e =>
        error"Error in getPlayers: $sortBy $order $isActive $standardMin $standardMax $rapidMin $rapidMax $blitzMin $blitzMax $query $page $size $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map(xs => GetPlayersOutput(xs, Option.when(xs.size == _size)(paging.nextPage.toString())))

  override def getPlayerById(id: PlayerId): IO[Player] =
    db.playerById(id.value)
      .handleErrorWith: e =>
        error"Error in getPlayerById: $id, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id))):
          _.transform.pure[IO]

  override def getPlayerByIds(ids: Set[PlayerId]): IO[GetPlayersByIdsOutput] =
    db.playersByIds(ids.map(_.value))
      .handleErrorWith: e =>
        error"Error in getPlayersByIds: $ids, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(p => p.id.toString -> p.transform).toMap)
      .map(GetPlayersByIdsOutput.apply)

  extension (playerInfo: fide.domain.PlayerInfo) inline def transform: Player = playerInfo.to[Player]

object Transformers:
  given Transformer.Derived[Int, Rating]          = Transformer.Derived.FromFunction(Rating.apply)
  given Transformer.Derived[String, FederationId] = Transformer.Derived.FromFunction(FederationId.apply)
  given Transformer.Derived[Int, PlayerId]        = Transformer.Derived.FromFunction(PlayerId.apply)
  given Transformer.Derived[OffsetDateTime, Timestamp] =
    Transformer.Derived.FromFunction(Timestamp.fromOffsetDateTime)
