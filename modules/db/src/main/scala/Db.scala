package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit]
  def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit]
  def playerById(id: PlayerId): IO[Option[PlayerInfo]]
  def countPlayers(filter: PlayerFilter): IO[Long]
  def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]]
  def allFederations: IO[List[FederationInfo]]
  def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]]
  def playersByFederationId(id: FederationId): IO[List[PlayerInfo]]
  def allFederationsSummary(paging: Pagination): IO[List[FederationSummary]]
  def countFederationsSummary: IO[Long]
  def countFederations: IO[Long]
  def federationSummaryById(id: FederationId): IO[Option[FederationSummary]]
  def upsertFederations(feds: List[NewFederation]): IO[Unit]
  def upsertPlayersWithHash(xs: List[(NewPlayer, Long)]): IO[Unit]
  def allPlayerHashes: IO[Map[PlayerId, Long]]
  def updateLastSeenAt(ids: List[PlayerId]): IO[Unit]

object Db:

  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit] =
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(Sql.upsertPlayer)
          federationCmd <- s.prepare(Sql.upsertFederation)
          _             <- s.transaction.use: _ =>
            federation.traverse(federationCmd.execute) *>
              playerCmd.execute(player)
        yield ()

    def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit] =
      val feds = xs.mapFilter(_._2).distinctBy(_.id)
      postgres.use: s =>
        for
          federationCmd <- s.prepare(Sql.upsertFederation)
          _             <- feds.traverse_(federationCmd.execute)
          playerCmd     <- s.prepare(Sql.upsertPlayers(xs.size))
          _             <- s.transaction.use: _ =>
            playerCmd.execute(xs.map(_._1))
        yield ()

    def playerById(id: PlayerId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerById)(id))

    def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]] =
      val f = Sql.allPlayers(sorting, paging, filter)
      val q = f.fragment.query(DbCodecs.playerInfo)
      postgres.use(_.execute(q)(f.argument))

    def countPlayers(filter: PlayerFilter): IO[Long] =
      val f = Sql.countPlayers(filter)
      val q = f.fragment.query(codec.all.int8)
      postgres.use(_.unique(q)(f.argument))

    def allFederations: IO[List[FederationInfo]] =
      postgres.use(_.execute(Sql.allFederations))

    def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]] =
      val f = Sql.playersByIds(ids.size)
      val q = f.query(DbCodecs.playerInfo)
      postgres.use(_.execute(q)(ids.toList))

    def playersByFederationId(id: FederationId): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByFederationId)(id))

    def allFederationsSummary(paging: Pagination): IO[List[FederationSummary]] =
      val f = Sql.allFederationsSummary(paging)
      val q = f.fragment.query(DbCodecs.federationSummary)
      postgres.use(_.execute(q)(f.argument))

    def countFederationsSummary: IO[Long] =
      postgres.use(_.unique(Sql.countFederationsSummary))

    def countFederations: IO[Long] =
      postgres.use(_.unique(Sql.countFederations))

    def federationSummaryById(id: FederationId): IO[Option[FederationSummary]] =
      postgres.use(_.option(Sql.federationSummaryById)(id))

    def upsertFederations(feds: List[NewFederation]): IO[Unit] =
      postgres.use: s =>
        s.prepare(Sql.upsertFederation)
          .flatMap: cmd =>
            feds.traverse_(cmd.execute)

    def upsertPlayersWithHash(xs: List[(NewPlayer, Long)]): IO[Unit] =
      xs.grouped(2000).toList.traverse_ { chunk =>
        val cmd = Sql.upsertPlayersWithHash(chunk.size)
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def allPlayerHashes: IO[Map[PlayerId, Long]] =
      postgres.use(_.execute(Sql.allPlayerHashes)).map(_.toMap)

    def updateLastSeenAt(ids: List[PlayerId]): IO[Unit] =
      ids.grouped(5000).toList.traverse_ { chunk =>
        val cmd = Sql.updateLastSeenAt(chunk.size)
        postgres.use(_.execute(cmd)(chunk)).void
      }

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import DbCodecs.*

  val upsertPlayer: Command[NewPlayer] =
    sql"""
        $insertIntoPlayer
        VALUES ($newPlayer)
        $onPlayerConflictDoUpdate
       """.command

  // TODO use returning
  def upsertPlayers(n: Int): Command[List[NewPlayer]] =
    val players = newPlayer.values.list(n)
    sql"""
        $insertIntoPlayer
        VALUES $players
        $onPlayerConflictDoUpdate
       """.command

  lazy val playerById: Query[PlayerId, PlayerInfo] =
    sql"$allPlayersFragment WHERE p.id = $playerIdCodec".query(playerInfo)

  lazy val playersByFederationId: Query[FederationId, PlayerInfo] =
    sql"$allPlayersFragment WHERE p.federation_id = $federationIdCodec".query(playerInfo)

  lazy val upsertFederation: Command[NewFederation] =
    sql"$insertIntoFederation VALUES ($newFederation) $onConflictDoNothing".command

  def upsertFederations(n: Int): Command[List[NewFederation]] =
    val feds = newFederation.values.list(n)
    sql"$insertIntoFederation VALUES $feds $onConflictDoNothing".command

  lazy val allFederations: Query[Void, FederationInfo] =
    sql"""
        SELECT id, name
        FROM federations
       """.query(federationInfo)

  def allPlayers(sorting: Sorting, page: Pagination, filter: PlayerFilter): AppliedFragment =
    allPlayersFragment(Void) |+| filterFragment(filter).fold(void)(where |+| _) |+|
      sortingFragment(sorting) |+| pagingFragment(page)

  def countPlayers(filter: PlayerFilter) =
    val select = sql"""
      select count(*) from players as p
        """.apply(Void)
    select |+| filterFragment(filter).fold(void)(where |+| _)

  val countFederations: Query[Void, Long] =
    sql"SELECT count(*) FROM federations".query(codec.all.int8)

  val countFederationsSummary: Query[Void, Long] =
    sql"SELECT count(*) FROM federations_summary".query(codec.all.int8)

  def playersByIds(n: Int): Fragment[List[PlayerId]] =
    val ids = playerIdCodec.values.list(n)
    sql"$allPlayersFragment WHERE p.id IN ($ids)"

  def allFederationsSummary(paging: Pagination): AppliedFragment =
    allFederationsSummaryFragment(Void) |+| pagingFragment(paging)

  lazy val federationSummaryById: Query[FederationId, FederationSummary] =
    sql"""$allFederationsSummaryFragment
        WHERE id = $federationIdCodec""".query(federationSummary)

  private val void: AppliedFragment  = sql"".apply(Void)
  private val where: AppliedFragment = sql"WHERE ".apply(Void)

  private def filterFragment(filter: PlayerFilter): Option[AppliedFragment] =
    FilterSql.filterFragment(TableAliases.players)(filter)

  private lazy val insertIntoPlayer =
    sql"""INSERT INTO players (id, name, title, women_title, other_titles, standard, standard_kfactor, rapid,
      rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id)"""

  private lazy val onPlayerConflictDoUpdate =
    sql"""
        ON CONFLICT (id) DO UPDATE SET (name, title, women_title, other_titles, standard, standard_kfactor, rapid,
          rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.women_title, EXCLUDED.other_titles, EXCLUDED.standard,
          EXCLUDED.standard_kfactor, EXCLUDED.rapid, EXCLUDED.rapid_kfactor, EXCLUDED.blitz, EXCLUDED.blitz_kfactor,
          EXCLUDED.sex, EXCLUDED.birth_year, EXCLUDED.active, EXCLUDED.federation_id)"""

  private val onConflictDoNothing  = sql"ON CONFLICT DO NOTHING"
  private val insertIntoFederation = sql"INSERT INTO federations (id, name)"

  private def pagingFragment(page: Pagination): AppliedFragment =
    sql"""
        LIMIT ${int4} OFFSET ${int4}""".apply(page.size, page.offset)

  private def sortingFragment(sorting: Sorting): AppliedFragment =
    val column = sorting.sortBy match
      case SortBy.Name      => "p.name"
      case SortBy.Standard  => "p.standard"
      case SortBy.Rapid     => "p.rapid"
      case SortBy.Blitz     => "p.blitz"
      case SortBy.BirthYear => "p.birth_year"
    val orderBy = sorting.orderBy match
      case Order.Asc  => "ASC"
      case Order.Desc => "DESC"
    sql"""
        ORDER BY #$column #$orderBy NULLS LAST""".apply(Void)

  lazy val allPlayerHashes: Query[Void, (PlayerId, Long)] =
    sql"SELECT id, hash FROM players".query(playerIdCodec *: int8)

  private val newPlayerWithHash: Codec[(NewPlayer, Long)] =
    (newPlayer *: int8).imap[(NewPlayer, Long)] { case p *: h *: EmptyTuple => (p, h) } { case (p, h) =>
      p *: h *: EmptyTuple
    }

  def upsertPlayersWithHash(n: Int): Command[List[(NewPlayer, Long)]] =
    val players = newPlayerWithHash.values.list(n)
    sql"""
        INSERT INTO players (id, name, title, women_title, other_titles, standard, standard_kfactor, rapid,
          rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash)
        VALUES $players
        ON CONFLICT (id) DO UPDATE SET (name, title, women_title, other_titles, standard, standard_kfactor, rapid,
          rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.women_title, EXCLUDED.other_titles, EXCLUDED.standard,
          EXCLUDED.standard_kfactor, EXCLUDED.rapid, EXCLUDED.rapid_kfactor, EXCLUDED.blitz, EXCLUDED.blitz_kfactor,
          EXCLUDED.sex, EXCLUDED.birth_year, EXCLUDED.active, EXCLUDED.federation_id, EXCLUDED.hash)
       """.command

  def updateLastSeenAt(n: Int): Command[List[PlayerId]] =
    val ids = playerIdCodec.values.list(n)
    sql"UPDATE players SET last_seen_at = now() WHERE id IN ($ids)".command

  private lazy val allPlayersFragment: Fragment[Void] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.other_titles, p.standard, p.standard_kfactor, p.rapid,
        p.rapid_kfactor, p.blitz, p.blitz_kfactor, p.sex, p.birth_year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p LEFT JOIN federations AS f ON p.federation_id = f.id
      """

  private lazy val allFederationsSummaryFragment: Fragment[Void] =
    sql"""
        SELECT id, name, players, avg_top_standard_rank, standard_players, coalesce(avg_top_standard, 0), avg_top_rapid_rank, rapid_players, coalesce(avg_top_rapid, 0), avg_top_blitz_rank, blitz_players, coalesce(avg_top_blitz, 0)
        FROM federations_summary"""
