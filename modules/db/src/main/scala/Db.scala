package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import skunk.*

trait Db:
  def upsert(info: NewPlayerInfo, history: NewPlayerHistory, federation: Option[NewFederation]): IO[Unit]
  def upsert(xs: List[(NewPlayerInfo, NewPlayerHistory, Option[NewFederation])]): IO[Unit]
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

object Db:

  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsert(info: NewPlayerInfo, history: NewPlayerHistory, federation: Option[NewFederation]): IO[Unit] =
      postgres.use: s =>
        for
          federationCmd <- s.prepare(Sql.upsertFederation)
          infoCmd       <- s.prepare(Sql.upsertPlayerInfo)
          historyCmd    <- s.prepare(Sql.upsertPlayerHistory)
          _             <- s.transaction.use: _ =>
            federation.traverse(federationCmd.execute) *>
              infoCmd.execute(info) *>
              historyCmd.execute(history)
        yield ()

    def upsert(xs: List[(NewPlayerInfo, NewPlayerHistory, Option[NewFederation])]): IO[Unit] =
      val feds = xs.mapFilter(_._3).distinct
      postgres.use: s =>
        for
          federationCmd <- s.prepare(Sql.upsertFederations(feds.size))
          infoCmd       <- s.prepare(Sql.upsertPlayerInfos(xs.size))
          historyCmd    <- s.prepare(Sql.upsertPlayerHistories(xs.size))
          _             <- s.transaction.use: _ =>
            federationCmd.execute(feds) *>
              infoCmd.execute(xs.map(_._1)) *>
              historyCmd.execute(xs.map(_._2))
        yield ()

    def playerById(id: PlayerId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerById)(id))

    def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]] =
      val f = Sql.allPlayers(sorting, paging, filter)
      val q = f.fragment.query(Codecs.playerInfo)
      postgres.use(_.execute(q)(f.argument))

    def countPlayers(filter: PlayerFilter): IO[Long] =
      val f = Sql.countPlayers(filter)
      val q = f.fragment.query(codec.all.int8)
      postgres.use(_.unique(q)(f.argument))

    def allFederations: IO[List[FederationInfo]] =
      postgres.use(_.execute(Sql.allFederations))

    def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]] =
      val f = Sql.playersByIds(ids.size)
      val q = f.query(Codecs.playerInfo)
      postgres.use(_.execute(q)(ids.toList))

    def playersByFederationId(id: FederationId): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByFederationId)(id))

    def allFederationsSummary(paging: Pagination): IO[List[FederationSummary]] =
      val f = Sql.allFederationsSummary(paging)
      val q = f.fragment.query(Codecs.federationSummary)
      postgres.use(_.execute(q)(f.argument))

    def countFederationsSummary: IO[Long] =
      postgres.use(_.unique(Sql.countFederationsSummary))

    def countFederations: IO[Long] =
      postgres.use(_.unique(Sql.countFederations))

    def federationSummaryById(id: FederationId): IO[Option[FederationSummary]] =
      postgres.use(_.option(Sql.federationSummaryById)(id))

private object Codecs:

  import skunk.codec.all.*
  import skunk.data.{ Arr, Type }

  import io.github.iltotore.iron.constraint.all.*
  import fide.db.iron.*

  val title: Codec[Title]           = `enum`[Title](_.value, Title.apply, Type("title"))
  val otherTitle: Codec[OtherTitle] = `enum`[OtherTitle](_.value, OtherTitle.apply, Type("other_title"))
  val gender: Codec[Gender]         = `enum`[Gender](_.value, Gender.apply, Type("sex"))
  val ratingCodec: Codec[Rating]    = int4.refined[RatingConstraint].imap(Rating.apply)(_.value)
  val federationIdCodec: Codec[FederationId] = text.refined[NonEmpty].imap(FederationId.apply)(_.value)
  val playerIdCodec: Codec[PlayerId]         = int4.refined[Positive].imap(PlayerId.apply)(_.value)

  val otherTitleArr: Codec[Arr[OtherTitle]] =
    Codec.array(
      _.value,
      OtherTitle(_).toRight("invalid title"),
      Type("_other_title", List(Type("other_title")))
    )

  val otherTitles: Codec[List[OtherTitle]] = otherTitleArr.opt.imap(_.fold(Nil)(_.toList))(Arr(_*).some)

  val newPlayerInfo: Codec[NewPlayerInfo] =
    (playerIdCodec *: text *: gender.opt *: int4.opt)
      .to[NewPlayerInfo]

  val newPlayerHistory: Codec[NewPlayerHistory] =
    (playerIdCodec *: int2 *: title.opt *: title.opt *: otherTitles *: federationIdCodec.opt *: bool *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt)
      .to[NewPlayerHistory]

  val newFederation: Codec[NewFederation] =
    (federationIdCodec *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (federationIdCodec *: text).to[FederationInfo]

  val stats: Codec[Stats] =
    (int4 *: int4 *: int4).to[Stats]

  val federationSummary: Codec[FederationSummary] =
    (federationIdCodec *: text *: int4 *: stats *: stats *: stats).to[FederationSummary]

  // Column order matches the allPlayersFragment SELECT:
  // pi.id, pi.name, pc.title, pc.women_title, pc.other_titles,
  // pc.standard, pc.standard_kfactor, pc.rapid, pc.rapid_kfactor, pc.blitz, pc.blitz_kfactor,
  // pi.sex, pi.birth_year, pc.active, pi.updated_at, pi.created_at, f.id, f.name
  val playerInfo: Codec[PlayerInfo] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import Codecs.*

  // -- Upsert: player_info --

  val upsertPlayerInfo: Command[NewPlayerInfo] =
    sql"""
        $insertIntoPlayerInfo
        VALUES ($newPlayerInfo)
        $onPlayerInfoConflictDoUpdate
       """.command

  def upsertPlayerInfos(n: Int): Command[List[NewPlayerInfo]] =
    val infos = newPlayerInfo.values.list(n)
    sql"""
        $insertIntoPlayerInfo
        VALUES $infos
        $onPlayerInfoConflictDoUpdate
       """.command

  // -- Upsert: player_history --

  val upsertPlayerHistory: Command[NewPlayerHistory] =
    sql"""
        $insertIntoPlayerHistory
        VALUES ($newPlayerHistory)
        $onPlayerHistoryConflictDoUpdate
       """.command

  def upsertPlayerHistories(n: Int): Command[List[NewPlayerHistory]] =
    val histories = newPlayerHistory.values.list(n)
    sql"""
        $insertIntoPlayerHistory
        VALUES $histories
        $onPlayerHistoryConflictDoUpdate
       """.command

  // -- Upsert: federations --

  lazy val upsertFederation: Command[NewFederation] =
    sql"$insertIntoFederation VALUES ($newFederation) $onConflictDoNothing".command

  def upsertFederations(n: Int): Command[List[NewFederation]] =
    val feds = newFederation.values.list(n)
    sql"$insertIntoFederation VALUES $feds $onConflictDoNothing".command

  // -- Read queries --

  lazy val playerById: Query[PlayerId, PlayerInfo] =
    sql"$allPlayersFragment WHERE pi.id = $playerIdCodec".query(playerInfo)

  lazy val playersByFederationId: Query[FederationId, PlayerInfo] =
    sql"$allPlayersFragment WHERE pc.federation_id = $federationIdCodec".query(playerInfo)

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
      SELECT count(*) FROM player_info AS pi
        LEFT JOIN player_current AS pc ON pi.id = pc.player_id
        """.apply(Void)
    select |+| filterFragment(filter).fold(void)(where |+| _)

  val countFederations: Query[Void, Long] =
    sql"SELECT count(*) FROM federations".query(codec.all.int8)

  val countFederationsSummary: Query[Void, Long] =
    sql"SELECT count(*) FROM federations".query(codec.all.int8)

  def playersByIds(n: Int): Fragment[List[PlayerId]] =
    val ids = playerIdCodec.values.list(n)
    sql"$allPlayersFragment WHERE pi.id IN ($ids)"

  def allFederationsSummary(paging: Pagination): AppliedFragment =
    allFederationsSummaryFragment(Void) |+| pagingFragment(paging)

  lazy val federationSummaryById: Query[FederationId, FederationSummary] =
    sql"""$allFederationsSummaryFragment
        WHERE id = $federationIdCodec""".query(federationSummary)

  // -- Fragments --

  private val void: AppliedFragment  = sql"".apply(Void)
  private val and: AppliedFragment   = sql" AND ".apply(Void)
  private val where: AppliedFragment = sql"WHERE ".apply(Void)

  // Maps a SortBy column to the correct table alias
  private def qualifiedColumn(sortBy: SortBy): String =
    sortBy match
      case SortBy.Name | SortBy.BirthYear => s"pi.${sortBy.value}"
      case _                              => s"pc.${sortBy.value}"

  private def between(table: String, column: String, range: RatingRange): Option[AppliedFragment] =
    between(s"$table.$column", range.min, range.max)

  private def between[A <: Int](qualifiedCol: String, min: Option[A], max: Option[A]): Option[AppliedFragment] =
    (min, max) match
      case (Some(min), Some(max)) =>
        sql"""
            #$qualifiedCol BETWEEN ${int4} AND ${int4}""".apply(min, max).some
      case (Some(min), None) =>
        sql"""
            #$qualifiedCol >= ${int4}""".apply(min).some
      case (None, Some(max)) =>
        sql"""
            #$qualifiedCol <= ${int4}""".apply(max).some
      case (None, None) => none

  private def filterFragment(filter: PlayerFilter): Option[AppliedFragment] =
    List.concat(
      filter.name.map(nameLikeFragment),
      between("pc", "standard", filter.standard),
      between("pc", "rapid", filter.rapid),
      between("pc", "blitz", filter.blitz),
      filter.isActive.map(filterActive),
      filter.federationId.map(federationIdFragment),
      filter.titles.map(xs => playersByTitles(xs.size)(xs, xs)),
      filter.otherTitles.map(xs => playersByOtherTitles(xs.size)(xs)),
      filter.gender.map(filterGender),
      between("pi.birth_year", filter.birthYearMin, filter.birthYearMax),
      filter.hasTitle.map(hasTitle),
      filter.hasWomenTitle.map(hasWomenTitle),
      filter.hasOtherTitle.map(hasOtherTitle)
    ) match
      case Nil => none
      case xs  => xs.intercalate(and).some

  private lazy val filterActive: Fragment[Boolean] =
    sql"pc.active = $bool"

  private lazy val filterGender: Fragment[Gender] =
    sql"pi.sex = $gender"

  def playersByTitles(n: Int): Fragment[(List[Title], List[Title])] =
    val titles = title.values.list(n)
    sql"(pc.title IN ($titles) OR pc.women_title in ($titles))"

  private def hasTitle: Boolean => AppliedFragment =
    case true  => sql"pc.title IS NOT NULL".apply(Void)
    case false => sql"pc.title IS NULL".apply(Void)

  private def hasWomenTitle: Boolean => AppliedFragment =
    case true  => sql"pc.women_title IS NOT NULL".apply(Void)
    case false => sql"pc.women_title IS NULL".apply(Void)

  private def hasOtherTitle: Boolean => AppliedFragment =
    case true  => sql"cardinality(pc.other_titles) != 0".apply(Void)
    case false => sql"cardinality(pc.other_titles) = 0".apply(Void)

  def playersByOtherTitles(n: Int): Fragment[List[OtherTitle]] =
    val otherTitles = otherTitle.values.list(n)
    sql"pc.other_titles && array[$otherTitles]::other_title[]"

  // -- Insert/conflict fragments --

  private lazy val insertIntoPlayerInfo =
    sql"""INSERT INTO player_info (id, name, sex, birth_year)"""

  private lazy val onPlayerInfoConflictDoUpdate =
    sql"""
        ON CONFLICT (id) DO UPDATE SET (name, sex, birth_year) =
        (EXCLUDED.name, EXCLUDED.sex, EXCLUDED.birth_year)"""

  private lazy val insertIntoPlayerHistory =
    sql"""INSERT INTO player_history (player_id, month, title, women_title, other_titles,
      federation_id, active, standard, standard_kfactor, rapid, rapid_kfactor, blitz, blitz_kfactor)"""

  private lazy val onPlayerHistoryConflictDoUpdate =
    sql"""
        ON CONFLICT (player_id, month) DO UPDATE SET (title, women_title, other_titles,
          federation_id, active, standard, standard_kfactor, rapid, rapid_kfactor, blitz, blitz_kfactor) =
        (EXCLUDED.title, EXCLUDED.women_title, EXCLUDED.other_titles,
          EXCLUDED.federation_id, EXCLUDED.active, EXCLUDED.standard, EXCLUDED.standard_kfactor,
          EXCLUDED.rapid, EXCLUDED.rapid_kfactor, EXCLUDED.blitz, EXCLUDED.blitz_kfactor)"""

  private val onConflictDoNothing  = sql"ON CONFLICT DO NOTHING"
  private val insertIntoFederation = sql"INSERT INTO federations (id, name)"

  private def nameLikeFragment(name: String): AppliedFragment =
    sql"""
        pi.name % $text""".apply(name)

  private def pagingFragment(page: Pagination): AppliedFragment =
    sql"""
        LIMIT ${int4} OFFSET ${int4}""".apply(page.size, page.offset)

  private def federationIdFragment(id: FederationId): AppliedFragment =
    if id.value.toLowerCase == "non" then sql"pc.federation_id IS NULL".apply(Void)
    else sql"""pc.federation_id = $federationIdCodec""".apply(id)

  private def sortingFragment(sorting: Sorting): AppliedFragment =
    val column  = qualifiedColumn(sorting.sortBy)
    val orderBy = sorting.orderBy.value
    sql"""
        ORDER BY #$column #$orderBy NULLS LAST""".apply(Void)

  private lazy val allPlayersFragment: Fragment[Void] =
    sql"""
        SELECT pi.id, pi.name, pc.title, pc.women_title, pc.other_titles,
        pc.standard, pc.standard_kfactor, pc.rapid, pc.rapid_kfactor, pc.blitz, pc.blitz_kfactor,
        pi.sex, pi.birth_year, pc.active, pi.updated_at, pi.created_at, f.id, f.name
        FROM player_info AS pi
        LEFT JOIN player_current AS pc ON pi.id = pc.player_id
        LEFT JOIN federations AS f ON pc.federation_id = f.id
      """

  private lazy val allFederationsSummaryFragment: Fragment[Void] =
    sql"""
        SELECT id, name, players, avg_top_standard_rank, standard_players, coalesce(avg_top_standard, 0), avg_top_rapid_rank, rapid_players, coalesce(avg_top_rapid, 0), avg_top_blitz_rank, blitz_players, coalesce(avg_top_blitz, 0)
        FROM federations_summary"""
