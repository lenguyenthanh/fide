package fide
package db

import cats.effect.*
import fide.domain.*
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: NewFederation): IO[Unit]
  // def byId(id: PlayerId): IO[Player]
  // def all: IO[List[Player]]

object Db:
  import Sql.*
  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsert(player: NewPlayer, federation: NewFederation): IO[Unit] =
      postgres.use: s =>
        for
          playerCmd           <- s.prepare(upsertPlayer)
          federationCmd       <- s.prepare(upsertFederation)
          playerFederationCmd <- s.prepare(upsertPlayerFederation)
          _ <- s.transaction.use: _ =>
            playerCmd.execute(player) *>
              federationCmd.execute(federation) *>
              playerFederationCmd.execute(player.id -> federation.id).void
        yield ()

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import skunk.data.Type

  val title: Codec[Title] = `enum`[Title](_.value, Title.apply, Type("title"))

  private val newPlayer: Codec[NewPlayer] =
    (int4 *: text *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool.opt)
      .to[NewPlayer]

  private val newFederation: Codec[NewFederation] =
    (text *: text).to[NewFederation]

  val upsertPlayer: Command[NewPlayer] =
    sql"""
        INSERT INTO players (id, name, title, standard, rapid, blitz, year, active)
        VALUES ($newPlayer)
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active)
       """.command

  val upsertFederation: Command[NewFederation] =
    sql"""
        INSERT INTO federations (id, name)
        VALUES ($newFederation)
        ON CONFLICT DO NOTHING
       """.command

  val upsertPlayerFederation: Command[(PlayerId, FederationId)] =
    sql"""
        INSERT INTO player_federation (player_id, federation_id)
        VALUES ($int4, $text)
        ON CONFLICT DO NOTHING
       """.command
