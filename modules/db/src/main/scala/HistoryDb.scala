package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait HistoryDb:
  def upsertPlayerInfo(players: List[PlayerInfoRow]): IO[Unit]
  def upsertPlayerInfoWithHash(players: List[(PlayerInfoRow, Long)]): IO[Unit]
  def upsertPlayerHistory(snapshots: List[PlayerHistoryRow]): IO[Unit]
  def allPlayerInfoHashes: IO[Map[PlayerId, Long]]
  def playerById(id: PlayerId, month: YearMonth): IO[Option[HistoricalPlayerInfo]]
  def allPlayers(
      month: YearMonth,
      sorting: Sorting,
      paging: Pagination,
      filter: PlayerFilter
  ): IO[List[HistoricalPlayerInfo]]
  def countPlayers(month: YearMonth, filter: PlayerFilter): IO[Long]
  def federationsSummary(month: YearMonth, paging: Pagination): IO[List[FederationSummary]]
  def countFederationsSummary(month: YearMonth): IO[Long]
  def federationSummaryById(id: FederationId, month: YearMonth): IO[Option[FederationSummary]]
  def availableMonths: IO[List[YearMonth]]

object HistoryDb:

  // 32767 max params / 13 params per history row ≈ 2520; use 2000 for safety
  private val ChunkSize = 2000

  def apply(postgres: Resource[IO, Session[IO]]): HistoryDb = new:

    def upsertPlayerInfo(players: List[PlayerInfoRow]): IO[Unit] =
      players.grouped(ChunkSize).toList.traverse_ { chunk =>
        val codec = DbCodecs.playerInfoRow.values.list(chunk.size)
        val cmd   = sql"""
          INSERT INTO player_info (id, name, sex, birth_year)
          VALUES $codec
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, sex = EXCLUDED.sex, birth_year = EXCLUDED.birth_year""".command
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def upsertPlayerInfoWithHash(players: List[(PlayerInfoRow, Long)]): IO[Unit] =
      players.grouped(ChunkSize).toList.traverse_ { chunk =>
        val codec = (DbCodecs.playerInfoRow *: int8)
          .imap[(PlayerInfoRow, Long)] { case r *: h *: EmptyTuple =>
            (r, h)
          } { case (r, h) => r *: h *: EmptyTuple }
          .values
          .list(chunk.size)
        val cmd = sql"""
          INSERT INTO player_info (id, name, sex, birth_year, hash)
          VALUES $codec
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, sex = EXCLUDED.sex, birth_year = EXCLUDED.birth_year,
            hash = EXCLUDED.hash""".command
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def upsertPlayerHistory(snapshots: List[PlayerHistoryRow]): IO[Unit] =
      snapshots.grouped(ChunkSize).toList.traverse_ { chunk =>
        val codec = DbCodecs.playerHistoryRow.values.list(chunk.size)
        val cmd   = sql"""
          INSERT INTO player_history (player_id, year_month, title, women_title, other_titles, standard,
            standard_kfactor, rapid, rapid_kfactor, blitz, blitz_kfactor, federation_id, active)
          VALUES $codec
          ON CONFLICT (player_id, year_month) DO UPDATE SET
            title = EXCLUDED.title, women_title = EXCLUDED.women_title, other_titles = EXCLUDED.other_titles,
            standard = EXCLUDED.standard, standard_kfactor = EXCLUDED.standard_kfactor,
            rapid = EXCLUDED.rapid, rapid_kfactor = EXCLUDED.rapid_kfactor,
            blitz = EXCLUDED.blitz, blitz_kfactor = EXCLUDED.blitz_kfactor,
            federation_id = EXCLUDED.federation_id, active = EXCLUDED.active""".command
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def playerById(id: PlayerId, month: YearMonth): IO[Option[HistoricalPlayerInfo]] =
      val f = Sql.playerByIdQuery(month, id)
      val q = f.fragment.query(DbCodecs.historicalPlayerInfo)
      postgres.use(_.option(q)(f.argument))

    def allPlayers(
        month: YearMonth,
        sorting: Sorting,
        paging: Pagination,
        filter: PlayerFilter
    ): IO[List[HistoricalPlayerInfo]] =
      val f = Sql.allHistoricalPlayers(month, sorting, paging, filter)
      val q = f.fragment.query(DbCodecs.historicalPlayerInfo)
      postgres.use(_.execute(q)(f.argument))

    def countPlayers(month: YearMonth, filter: PlayerFilter): IO[Long] =
      val f = Sql.countHistoricalPlayers(month, filter)
      val q = f.fragment.query(int8)
      postgres.use(_.unique(q)(f.argument))

    def federationsSummary(month: YearMonth, paging: Pagination): IO[List[FederationSummary]] =
      val f = Sql.historicalFederationsSummary(month, paging)
      val q = f.fragment.query(DbCodecs.federationSummary)
      postgres.use(_.execute(q)(f.argument))

    def countFederationsSummary(month: YearMonth): IO[Long] =
      val f = Sql.countHistoricalFederationsSummary(month)
      val q = f.fragment.query(int8)
      postgres.use(_.unique(q)(f.argument))

    def federationSummaryById(id: FederationId, month: YearMonth): IO[Option[FederationSummary]] =
      val f = Sql.historicalFederationSummaryById(id, month)
      val q = f.fragment.query(DbCodecs.federationSummary)
      postgres.use(_.option(q)(f.argument))

    def allPlayerInfoHashes: IO[Map[PlayerId, Long]] =
      postgres.use(_.execute(Sql.allPlayerInfoHashes)).map(_.toMap)

    def availableMonths: IO[List[YearMonth]] =
      postgres.use(_.execute(Sql.availableMonths))

  private object Sql:

    import DbCodecs.*

    private val void: AppliedFragment = sql"".apply(Void)
    private val and: AppliedFragment  = sql" AND ".apply(Void)

    def playerByIdQuery(month: YearMonth, id: PlayerId): AppliedFragment =
      allHistoricalPlayersFragment(month) |+| and |+|
        sql"ph.player_id = ${DbCodecs.playerIdCodec}".apply(id)

    // The base fragment selects from player_history joined with player_info and federations.
    // Column aliases: ph = player_history, pi = player_info, f = federations
    // The WHERE ph.year_month = $month is always applied.
    def allHistoricalPlayersFragment(month: YearMonth): AppliedFragment =
      sql"""
        SELECT pi.id, pi.name, ph.year_month, ph.title, ph.women_title, ph.other_titles,
          ph.standard, ph.standard_kfactor, ph.rapid, ph.rapid_kfactor, ph.blitz, ph.blitz_kfactor,
          pi.sex, pi.birth_year, ph.active, f.id, f.name
        FROM player_history AS ph
        JOIN player_info AS pi ON ph.player_id = pi.id
        LEFT JOIN federations AS f ON ph.federation_id = f.id
        WHERE ph.year_month = $yearMonthCodec""".apply(month)

    def allHistoricalPlayers(
        month: YearMonth,
        sorting: Sorting,
        page: Pagination,
        filter: PlayerFilter
    ): AppliedFragment =
      allHistoricalPlayersFragment(month) |+| filterFragment(filter).fold(void)(and |+| _) |+|
        sortingFragment(sorting) |+| pagingFragment(page)

    def countHistoricalPlayers(month: YearMonth, filter: PlayerFilter): AppliedFragment =
      val select = sql"""
        SELECT count(*)
        FROM player_history AS ph
        JOIN player_info AS pi ON ph.player_id = pi.id
        WHERE ph.year_month = $yearMonthCodec""".apply(month)
      select |+| filterFragment(filter).fold(void)(and |+| _)

    def historicalFederationsSummary(month: YearMonth, paging: Pagination): AppliedFragment =
      federationsSummaryBase(month) |+| pagingFragment(paging)

    def countHistoricalFederationsSummary(month: YearMonth): AppliedFragment =
      sql"""
        SELECT count(DISTINCT ph.federation_id)
        FROM player_history AS ph
        WHERE ph.year_month = $yearMonthCodec AND ph.active = true AND ph.federation_id IS NOT NULL
        """.apply(month)

    def historicalFederationSummaryById(id: FederationId, month: YearMonth): AppliedFragment =
      val fedFilter = sql"ph.federation_id = $federationIdCodec".apply(id)
      activePlayersAndRankedFedsCte(month, fedFilter) |+|
        sql"""
        SELECT f.id, f.name, rf.players::int,
          1::int, rf.standard_players::int, coalesce(rf.avg_top_standard, 0)::int,
          1::int, rf.rapid_players::int, coalesce(rf.avg_top_rapid, 0)::int,
          1::int, rf.blitz_players::int, coalesce(rf.avg_top_blitz, 0)::int
        FROM ranked_feds rf
        JOIN federations f ON rf.federation_id = f.id
        """.apply(Void)

    private def federationsSummaryBase(month: YearMonth): AppliedFragment =
      val fedFilter = sql"ph.federation_id IS NOT NULL".apply(Void)
      activePlayersAndRankedFedsCte(month, fedFilter) |+|
        sql""",
        summary AS (
          SELECT rf.*,
            rank() OVER (ORDER BY rf.avg_top_standard DESC NULLS LAST) as std_rank,
            rank() OVER (ORDER BY rf.avg_top_rapid DESC NULLS LAST) as rapid_rank,
            rank() OVER (ORDER BY rf.avg_top_blitz DESC NULLS LAST) as blitz_rank
          FROM ranked_feds rf
        )
        SELECT f.id, f.name, s.players::int,
          s.std_rank::int, s.standard_players::int, coalesce(s.avg_top_standard, 0)::int,
          s.rapid_rank::int, s.rapid_players::int, coalesce(s.avg_top_rapid, 0)::int,
          s.blitz_rank::int, s.blitz_players::int, coalesce(s.avg_top_blitz, 0)::int
        FROM summary s
        JOIN federations f ON s.federation_id = f.id
        ORDER BY s.avg_top_standard DESC NULLS LAST
        """.apply(Void)

    private def activePlayersAndRankedFedsCte(month: YearMonth, fedFilter: AppliedFragment): AppliedFragment =
      sql"""
        WITH active_players AS (
          SELECT ph.federation_id, ph.standard, ph.rapid, ph.blitz,
            row_number() OVER (PARTITION BY ph.federation_id ORDER BY ph.standard DESC NULLS LAST) as std_rn,
            row_number() OVER (PARTITION BY ph.federation_id ORDER BY ph.rapid DESC NULLS LAST) as rapid_rn,
            row_number() OVER (PARTITION BY ph.federation_id ORDER BY ph.blitz DESC NULLS LAST) as blitz_rn
          FROM player_history AS ph
          WHERE ph.year_month = $yearMonthCodec AND ph.active = true AND """.apply(month) |+| fedFilter |+|
        sql"""
        ),
        ranked_feds AS (
          SELECT
            ap.federation_id,
            count(*) as players,
            round(avg(ap.standard) FILTER (WHERE ap.std_rn <= 10))::int as avg_top_standard,
            count(ap.standard) as standard_players,
            round(avg(ap.rapid) FILTER (WHERE ap.rapid_rn <= 10))::int as avg_top_rapid,
            count(ap.rapid) as rapid_players,
            round(avg(ap.blitz) FILTER (WHERE ap.blitz_rn <= 10))::int as avg_top_blitz,
            count(ap.blitz) as blitz_players
          FROM active_players ap
          GROUP BY ap.federation_id
        )""".apply(Void)

    lazy val allPlayerInfoHashes: Query[Void, (PlayerId, Long)] =
      sql"SELECT id, hash FROM player_info".query(playerIdCodec *: int8)

    lazy val availableMonths: Query[Void, YearMonth] =
      sql"SELECT DISTINCT year_month FROM player_history ORDER BY year_month DESC".query(yearMonthCodec)

    private def filterFragment(filter: PlayerFilter): Option[AppliedFragment] =
      FilterSql.filterFragment(TableAliases.history)(filter)

    private def pagingFragment(page: Pagination): AppliedFragment =
      sql" LIMIT ${int4} OFFSET ${int4}".apply(page.size, page.offset)

    private def sortingFragment(sorting: Sorting): AppliedFragment =
      val a      = TableAliases.history
      val column = sorting.sortBy match
        case SortBy.Name      => s"${a.identity}.name"
        case SortBy.BirthYear => s"${a.identity}.birth_year"
        case other            => s"${a.data}.${other.value}"
      val orderBy = sorting.orderBy.value
      sql" ORDER BY #$column #$orderBy NULLS LAST".apply(Void)
