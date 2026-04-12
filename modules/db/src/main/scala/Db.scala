package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import skunk.*

trait Db:
  def upsertCrawlPlayers(
      xs: List[(CrawlPlayer, Option[NewFederation], Long)],
      yearMonth: YearMonth
  ): IO[Unit]
  def playerById(id: PlayerId): IO[Option[PlayerInfo]]
  def playerByFideId(fideId: FideId): IO[Option[PlayerInfo]]
  def countPlayers(filter: PlayerFilter): IO[Long]
  def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]]
  def allFederations: IO[List[FederationInfo]]
  def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]]
  def playersByFideIds(ids: Set[FideId]): IO[List[PlayerInfo]]
  def playersByFederationId(id: FederationId): IO[List[PlayerInfo]]
  def allFederationsSummary(paging: Pagination): IO[List[FederationSummary]]
  def countFederationsSummary: IO[Long]
  def countFederations: IO[Long]
  def federationSummaryById(id: FederationId): IO[Option[FederationSummary]]
  def upsertFederations(feds: List[NewFederation]): IO[Unit]
  def allPlayerHashesByFideId: IO[Map[FideId, Long]]
  def refreshFederationsSummary: IO[Unit]

object Db:

  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsertCrawlPlayers(
        xs: List[(CrawlPlayer, Option[NewFederation], Long)],
        yearMonth: YearMonth
    ): IO[Unit] =
      val feds = xs.mapFilter(_._2).distinctBy(_.id)
      postgres.use: s =>
        for
          federationCmd <- s.prepare(Sql.upsertFederation)
          _             <- feds.traverse_(federationCmd.execute)
          upsertInfoCmd <- s.prepare(Sql.upsertPlayerInfo)
          upsertCmd     <- s.prepare(Sql.upsertPlayerFromCrawl)
          historyCmd    <- s.prepare(Sql.upsertPlayerHistoryFromCrawl)
          _             <- s.transaction.use: _ =>
            xs.traverse_ { case (player, _, hash) =>
              val infoHash = CrawlPlayer.computeInfoHash(player)
              for
                playerId <- upsertInfoCmd.unique((player, infoHash))
                _        <- upsertCmd.execute((playerId, player, hash))
                _        <- historyCmd.execute((playerId, player, yearMonth))
              yield ()
            }
        yield ()

    def playerById(id: PlayerId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerById)(id))

    def playerByFideId(fideId: FideId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerByFideId)(fideId))

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
      if ids.isEmpty then IO.pure(Nil)
      else
        val f = Sql.playersByIds(ids.size)
        val q = f.query(DbCodecs.playerInfo)
        postgres.use(_.execute(q)(ids.toList))

    def playersByFideIds(ids: Set[FideId]): IO[List[PlayerInfo]] =
      if ids.isEmpty then IO.pure(Nil)
      else
        val f = Sql.playersByFideIds(ids.size)
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
      feds.grouped(1000).toList.traverse_ { chunk =>
        val cmd = Sql.upsertFederations(chunk.size)
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def allPlayerHashesByFideId: IO[Map[FideId, Long]] =
      fs2.Stream
        .resource(postgres)
        .flatMap(_.stream(Sql.allPlayerHashesByFideId)(Void, 4096))
        .compile
        .fold(Map.empty[FideId, Long])(_ + _)

    def refreshFederationsSummary: IO[Unit] =
      postgres.use(_.execute(Sql.refreshFederationsSummary)).void

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import DbCodecs.*

  /** Step 1: Upsert player_info — returns the (possibly new) PlayerId. Uses DO UPDATE SET fide_id =
    * EXCLUDED.fide_id as a no-op update to guarantee RETURNING always produces a row. The actual biographical
    * fields are only updated when the hash differs.
    */
  val upsertPlayerInfo: Query[(CrawlPlayer, Long), PlayerId] =
    sql"""
        INSERT INTO player_info (id, fide_id, name, sex, birth_year, hash)
        VALUES (nextval('player_info_id_seq'), $fideIdCodec, $text, ${gender.opt}, ${int4.opt}, $int8)
        ON CONFLICT (fide_id) DO UPDATE SET
          fide_id = EXCLUDED.fide_id,
          name = CASE WHEN player_info.hash != EXCLUDED.hash THEN EXCLUDED.name ELSE player_info.name END,
          sex = CASE WHEN player_info.hash != EXCLUDED.hash THEN EXCLUDED.sex ELSE player_info.sex END,
          birth_year = CASE WHEN player_info.hash != EXCLUDED.hash THEN EXCLUDED.birth_year ELSE player_info.birth_year END,
          hash = CASE WHEN player_info.hash != EXCLUDED.hash THEN EXCLUDED.hash ELSE player_info.hash END
        RETURNING id
       """
      .query(playerIdCodec)
      .contramap[(CrawlPlayer, Long)]: (p, infoHash) =>
        p.fideId *: p.name *: p.gender *: p.birthYear *: infoHash *: EmptyTuple

  /** Step 2: Upsert players — uses resolved PlayerId from step 1. */
  val upsertPlayerFromCrawl: Command[(PlayerId, CrawlPlayer, Long)] =
    sql"""
        INSERT INTO players (id, fide_id, name, title, women_title, other_titles, standard, standard_kfactor, rapid,
          rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash)
        VALUES ($playerIdCodec, $fideIdCodec, $text, ${title.opt}, ${title.opt}, $otherTitles, ${ratingCodec.opt},
          ${int4.opt}, ${ratingCodec.opt}, ${int4.opt}, ${ratingCodec.opt}, ${int4.opt}, ${gender.opt}, ${int4.opt},
          $bool, ${federationIdCodec.opt}, $int8)
        ON CONFLICT (fide_id) DO UPDATE SET (name, title, women_title, other_titles, standard, standard_kfactor, rapid,
          rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.women_title, EXCLUDED.other_titles, EXCLUDED.standard,
          EXCLUDED.standard_kfactor, EXCLUDED.rapid, EXCLUDED.rapid_kfactor, EXCLUDED.blitz, EXCLUDED.blitz_kfactor,
          EXCLUDED.sex, EXCLUDED.birth_year, EXCLUDED.active, EXCLUDED.federation_id, EXCLUDED.hash)
       """.command
      .contramap[(PlayerId, CrawlPlayer, Long)]: (id, p, hash) =>
        id *: p.fideId *: p.name *: p.title *: p.womenTitle *: p.otherTitles *: p.standard *: p.standardK *:
          p.rapid *: p.rapidK *: p.blitz *: p.blitzK *: p.gender *: p.birthYear *: p.active *: p.federationId *: hash *: EmptyTuple

  /** Step 3: Upsert player_history — monthly snapshot from crawl. */
  val upsertPlayerHistoryFromCrawl: Command[(PlayerId, CrawlPlayer, YearMonth)] =
    sql"""
        INSERT INTO player_history (player_id, fide_id, year_month, title, women_title, other_titles, standard,
          standard_kfactor, rapid, rapid_kfactor, blitz, blitz_kfactor, federation_id, active)
        VALUES ($playerIdCodec, $fideIdCodec, $yearMonthCodec, ${title.opt}, ${title.opt}, $otherTitles, ${ratingCodec.opt},
          ${int4.opt}, ${ratingCodec.opt}, ${int4.opt}, ${ratingCodec.opt}, ${int4.opt}, ${federationIdCodec.opt}, $bool)
        ON CONFLICT (player_id, year_month) DO UPDATE SET
          fide_id = EXCLUDED.fide_id, title = EXCLUDED.title, women_title = EXCLUDED.women_title,
          other_titles = EXCLUDED.other_titles, standard = EXCLUDED.standard, standard_kfactor = EXCLUDED.standard_kfactor,
          rapid = EXCLUDED.rapid, rapid_kfactor = EXCLUDED.rapid_kfactor, blitz = EXCLUDED.blitz,
          blitz_kfactor = EXCLUDED.blitz_kfactor, federation_id = EXCLUDED.federation_id, active = EXCLUDED.active
       """.command
      .contramap[(PlayerId, CrawlPlayer, YearMonth)]: (id, p, ym) =>
        id *: p.fideId *: ym *: p.title *: p.womenTitle *: p.otherTitles *: p.standard *: p.standardK *:
          p.rapid *: p.rapidK *: p.blitz *: p.blitzK *: p.federationId *: p.active *: EmptyTuple

  lazy val playerById: Query[PlayerId, PlayerInfo] =
    sql"$allPlayersFragment WHERE p.id = $playerIdCodec".query(playerInfo)

  lazy val playerByFideId: Query[FideId, PlayerInfo] =
    sql"$allPlayersFragment WHERE p.fide_id = $fideIdCodec".query(playerInfo)

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

  def playersByFideIds(n: Int): Fragment[List[FideId]] =
    val ids = fideIdCodec.values.list(n)
    sql"$allPlayersFragment WHERE p.fide_id IN ($ids)"

  def allFederationsSummary(paging: Pagination): AppliedFragment =
    allFederationsSummaryFragment(Void) |+|
      sql""" ORDER BY avg_top_standard DESC NULLS LAST""".apply(Void) |+| pagingFragment(paging)

  lazy val federationSummaryById: Query[FederationId, FederationSummary] =
    sql"""$allFederationsSummaryFragment
        WHERE id = $federationIdCodec""".query(federationSummary)

  private val void: AppliedFragment  = sql"".apply(Void)
  private val where: AppliedFragment = sql"WHERE ".apply(Void)

  private def filterFragment(filter: PlayerFilter): Option[AppliedFragment] =
    FilterSql.filterFragment(TableAliases.players)(filter)

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

  lazy val allPlayerHashesByFideId: Query[Void, (FideId, Long)] =
    sql"SELECT fide_id, hash FROM players WHERE fide_id IS NOT NULL".query(fideIdCodec *: int8)

  val refreshFederationsSummary: Command[Void] =
    sql"REFRESH MATERIALIZED VIEW CONCURRENTLY federations_summary".command

  private lazy val allPlayersFragment: Fragment[Void] =
    sql"""
        SELECT p.id, p.fide_id, p.name, p.title, p.women_title, p.other_titles, p.standard, p.standard_kfactor, p.rapid,
        p.rapid_kfactor, p.blitz, p.blitz_kfactor, p.sex, p.birth_year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p LEFT JOIN federations AS f ON p.federation_id = f.id
      """

  private lazy val allFederationsSummaryFragment: Fragment[Void] =
    sql"""
        SELECT id, name, players, avg_top_standard_rank, standard_players, coalesce(avg_top_standard, 0), avg_top_rapid_rank, rapid_players, coalesce(avg_top_rapid, 0), avg_top_blitz_rank, blitz_players, coalesce(avg_top_blitz, 0)
        FROM federations_summary"""
