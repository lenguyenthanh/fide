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
    info"getPlayers: page=$_page, sorting=$sorting, query=$query" *>
      query
        .fold(db.allPlayers(sorting, paging))(db.playersByName(_, sorting, paging))
        .map(_.map(_.transform))
        .map(xs => GetPlayersOutput(xs, Option.when(xs.size == _size)(paging.nextPage.toString())))

  override def getPlayerById(id: PlayerId): IO[Player] =
    db.playerById(id.value)
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id))):
          _.transform.pure[IO]

  override def getPlayerByIds(ids: Set[PlayerId]): IO[GetPlayersByIdsOutput] =
    db.playersByIds(ids.map(_.value))
      .map(_.map(p => p.id.toString -> p.transform).toMap)
      .map(GetPlayersByIdsOutput.apply)

  extension (playerInfo: fide.domain.PlayerInfo)
    def transform: Player =
      playerInfo
        .into[Player]
        .transform(Field.const(_.inactive, !playerInfo.active))

object Transformers:
  given Transformer.Derived[Int, Rating]          = Transformer.Derived.FromFunction(Rating.apply)
  given Transformer.Derived[String, FederationId] = Transformer.Derived.FromFunction(FederationId.apply)
  given Transformer.Derived[Int, PlayerId]        = Transformer.Derived.FromFunction(PlayerId.apply)
  given Transformer.Derived[OffsetDateTime, Timestamp] =
    Transformer.Derived.FromFunction(Timestamp.fromOffsetDateTime)
