package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db.Pagination
import fide.domain.*
import org.typelevel.log4cats.Logger
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit]
  def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit]
  def playerById(id: PlayerId): IO[Option[PlayerInfo]]
  def allPlayers(sorting: Sorting, page: Pagination): IO[List[PlayerInfo]]
  def allFederations: IO[List[FederationInfo]]
  def playersByName(name: String, page: Pagination): IO[List[PlayerInfo]]
  def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]]
  def playersByFederationId(id: FederationId): IO[List[PlayerInfo]]

object Db:

  // start at 1
  case class Pagination(limit: Int, offset: Int):
    def next     = copy(offset = offset + limit)
    def nextPage = (offset / limit) + 1

  object Pagination:
    val defaultLimit  = 30
    val defaultPage   = 1
    val defaultOffset = 0
    val default       = Pagination(defaultLimit, defaultOffset)

    def apply(limit: Option[Int], page: Option[Int]): Pagination =
      val _limit  = limit.getOrElse(defaultLimit)
      val _offset = (page.getOrElse(defaultPage) - 1) * _limit
      Pagination(_limit, _offset)

    def fromPageAndSize(page: Int, size: Int): Pagination =
      val offset = (math.max(defaultPage, page) - 1) * size
      Pagination(size, offset)

  import io.github.arainko.ducktape.*
  def apply(postgres: Resource[IO, Session[IO]])(using Logger[IO]): Db = new:
    def upsert(newPlayer: NewPlayer, federation: Option[NewFederation]): IO[Unit] =
      val player = newPlayer.toInsertPlayer(federation.map(_.id))
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(Sql.upsertPlayer)
          federationCmd <- s.prepare(Sql.upsertFederation)
          _ <- s.transaction.use: _ =>
            federation.traverse(federationCmd.execute) *>
              playerCmd.execute(player)
        yield ()

    def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit] =
      val players = xs.map((p, f) => p.toInsertPlayer(f.map(_.id)))
      val feds    = xs.flatMap(_._2).distinct
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(Sql.upsertPlayers(players.size))
          federationCmd <- s.prepare(Sql.upsertFederations(feds.size))
          _ <- s.transaction.use: _ =>
            federationCmd.execute(feds) *>
              playerCmd.execute(players)
        yield ()

    def playerById(id: PlayerId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerById)(id))

    def allPlayers(sorting: Sorting, page: Pagination): IO[List[PlayerInfo]] =
      val f = Sql.allPlayers(sorting, page)
      val q = f.fragment.query(Codecs.playerInfo)
      postgres.use:
        _.prepare(q).flatMap(_.stream(f.argument, page.limit).compile.toList)

    def allFederations: IO[List[FederationInfo]] =
      postgres.use(_.execute(Sql.allFederations))

    def playersByName(name: String, page: Pagination): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByName)(s"%$name%", page.limit, page.offset))

    def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByIds(ids.size))(ids.toList))

    def playersByFederationId(id: FederationId): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByFederationId)(id))

  extension (p: NewPlayer)
    def toInsertPlayer(fedId: Option[FederationId]) =
      p.into[InsertPlayer].transform(Field.const(_.federation, fedId))

private object Codecs:

  import skunk.codec.all.*
  import skunk.data.Type

  val title: Codec[Title] = `enum`[Title](_.value, Title.apply, Type("title"))

  val insertPlayer: Codec[InsertPlayer] =
    (int4 *: text *: title.opt *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool *: text.opt)
      .to[InsertPlayer]

  val newFederation: Codec[NewFederation] =
    (text *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (text *: text).to[FederationInfo]

  val playerInfo: Codec[PlayerInfo] =
    (int4 *: text *: title.opt *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import Codecs.*

  // TODO use returning
  val upsertPlayer: Command[InsertPlayer] =
    sql"""
        INSERT INTO players (id, name, title, women_title, standard, rapid, blitz, year, active, federation_id)
        VALUES ($insertPlayer)
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active, EXCLUDED.federation_id)
       """.command

  // TODO use returning
  def upsertPlayers(n: Int): Command[List[InsertPlayer]] =
    val players = insertPlayer.values.list(n)
    sql"""
        INSERT INTO players (id, name, title, women_title, standard, rapid, blitz, year, active, federation_id)
        VALUES $players
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active, EXCLUDED.federation_id)
       """.command

  val playerById: Query[PlayerId, PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.id = $int4 AND p.federation_id = f.id
       """.query(playerInfo)

  val playersByFederationId: Query[FederationId, PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = $text AND p.federation_id = f.id
       """.query(playerInfo)

  val upsertFederation: Command[NewFederation] =
    sql"""
        INSERT INTO federations (id, name)
        VALUES ($newFederation)
        ON CONFLICT DO NOTHING
       """.command

  def upsertFederations(n: Int): Command[List[NewFederation]] =
    val feds = newFederation.values.list(n)
    sql"""
        INSERT INTO federations (id, name)
        VALUES $feds
        ON CONFLICT DO NOTHING
       """.command

  val allFederations: Query[Void, FederationInfo] =
    sql"""
        SELECT id, name
        FROM federations
       """.query(federationInfo)

  def allPlayers(sorting: Sorting, page: Pagination): AppliedFragment =
    allPlayers(Void) |+| sortingFragment(sorting) |+| pagingFragment(page)

  private def pagingFragment(page: Pagination): AppliedFragment =
    sql"""
        LIMIT ${int4} OFFSET ${int4}
       """.apply(page.limit, page.offset)

  private def sortingFragment(sorting: Sorting): AppliedFragment =
    val column  = s"p.${sorting.sortBy.value}"
    val orderBy = sorting.orderBy.value
    sql"""
        ORDER BY #$column #$orderBy NULLS LAST
       """.apply(Void)

  val allPlayers: Fragment[Void] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = f.id
        """

  def playersByIds(n: Int): Query[List[Int], PlayerInfo] =
    val ids = int4.values.list(n)
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.id in ($ids) AND p.federation_id = f.id
       """.query(playerInfo)

  val playersByName: Query[(String, Int, Int), PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = f.id AND p.name ILIKE $text
        LIMIT ${int4} OFFSET ${int4}
       """.query(playerInfo)
