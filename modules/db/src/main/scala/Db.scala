package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit]
  def playerById(id: PlayerId): IO[PlayerInfo]
  // def all: IO[List[Player]]

object Db:
  import Sql.*
  import io.github.arainko.ducktape.*
  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsert(newPlayer: NewPlayer, federation: Option[NewFederation]): IO[Unit] =
      val player = newPlayer.into[InsertPlayer].transform(Field.const(_.federation, federation.map(_.id)))
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(upsertPlayer)
          federationCmd <- s.prepare(upsertFederation)
          _ <- s.transaction.use: _ =>
            federation.traverse(federationCmd.execute) *>
              playerCmd.execute(player)
        yield ()

    def playerById(id: PlayerId): IO[PlayerInfo] =
      postgres.use(_.unique(findPlayerById)(id))

private object Codecs:

  import skunk.codec.all.*
  import skunk.data.Type

  val title: Codec[Title] = `enum`[Title](_.value, Title.apply, Type("title"))

  val insertPlayer: Codec[InsertPlayer] =
    (int4 *: text *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool.opt *: text.opt)
      .to[InsertPlayer]

  val newFederation: Codec[NewFederation] =
    (text *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (text *: text).to[FederationInfo]

  val playerInfo: Codec[PlayerInfo] =
    (int4 *: text *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool.opt *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import Codecs.*

  // TODO use returning
  val upsertPlayer: Command[InsertPlayer] =
    sql"""
        INSERT INTO players (id, name, title, standard, rapid, blitz, year, active, federation_id)
        VALUES ($insertPlayer)
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active, EXCLUDED.federation_id)
       """.command

  val findPlayerById: Query[PlayerId, PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.id = $int4 AND p.federation_id = f.id
       """.query(playerInfo)

  val upsertFederation: Command[NewFederation] =
    sql"""
        INSERT INTO federations (id, name)
        VALUES ($newFederation)
        ON CONFLICT DO NOTHING
       """.command

  // val upsertPlayerFederation: Command[(PlayerId, FederationId)] =
  //   sql"""
  //       INSERT INTO player_federation (player_id, federation_id)
  //       VALUES ($int4, $text)
  //       ON CONFLICT DO NOTHING
  //      """.command
