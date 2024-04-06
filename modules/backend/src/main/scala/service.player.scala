package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.spec.*
import io.github.arainko.ducktape.*
import smithy4s.Timestamp

import java.time.OffsetDateTime

import Transformers.given

class PlayerServiceImpl(db: Db) extends PlayerService[IO]:

  override def getPlayers(
      query: Option[String],
      page: Option[String],
      size: Option[Int]
  ): IO[GetPlayersOutput] =
    val _size   = size.getOrElse(Db.Pagination.defaultLimit)
    val _offset = page.flatMap(_.toIntOption).getOrElse(Db.Pagination.defaultPage) * _size
    val _page   = Db.Pagination(_size, _offset)
    query
      .fold(db.allPlayers(_page))(db.playersByName.apply(_, _page))
      .map(_.map(_.transform))
      .map(xs => GetPlayersOutput(xs, Option.when(xs.size == _size)(_page.nextPage.toString())))

  override def getPlayerById(id: PlayerId): IO[Player] =
    db.playerById(id.value)
      .flatMap:
        _.fold(IO.raiseError(PlayerNotFound(id))):
          _.transform.pure[IO]

  override def getPlayerByIds(ids: List[PlayerId]): IO[GetPlayersOutput] =
    IO.raiseError(NotImplementedYetError("Thanh is too lazy to implement this"))

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
