package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import org.typelevel.log4cats.Logger
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit]
  def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit]
  def playerById(id: PlayerId): IO[Option[PlayerInfo]]
  def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]]
  def allFederations: IO[List[FederationInfo]]
  def playersByIds(ids: Set[PlayerId]): IO[List[PlayerInfo]]
  def playersByFederationId(id: FederationId): IO[List[PlayerInfo]]
  def allFederationsSummary(paging: Pagination): IO[List[FederationSummary]]
  def federationSummaryById(id: FederationId): IO[Option[FederationSummary]]

object Db:

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

    def allPlayers(sorting: Sorting, paging: Pagination, filter: PlayerFilter): IO[List[PlayerInfo]] =
      val f = Sql.allPlayers(sorting, paging, filter)
      val q = f.fragment.query(Codecs.playerInfo)
      postgres.use(_.execute(q)(f.argument))

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

    def federationSummaryById(id: FederationId): IO[Option[FederationSummary]] =
      postgres.use(_.option(Sql.federationSummaryById)(id))

  extension (p: NewPlayer)
    def toInsertPlayer(fedId: Option[FederationId]) =
      p.into[InsertPlayer].transform(Field.const(_.federation, fedId))

private object Codecs:

  import skunk.codec.all.*
  import skunk.data.{ Arr, Type }

  // copy from https://github.com/Iltotore/iron/blob/main/skunk/src/io.github.iltotore.iron/skunk.scala for skunk 1.0.0
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.all.*

  /** Explicit conversion for refining a [[Codec]]. Decodes to the underlying type then checks the constraint.
    *
    * @param constraint
    *   the [[Constraint]] implementation to test the decoded value
    */
  extension [A](codec: Codec[A])
    inline def refined[C](using inline constraint: Constraint[A, C]): Codec[A :| C] =
      codec.eimap[A :| C](_.refineEither[C])(_.asInstanceOf[A])

  /** A [[Codec]] for refined types. Decodes to the underlying type then checks the constraint.
    *
    * @param codec
    *   the [[Codec]] of the underlying type
    * @param constraint
    *   the [[Constraint]] implementation to test the decoded value
    */
  inline given [A, C] => (inline codec: Codec[A], inline constraint: Constraint[A, C]) => Codec[A :| C] =
    codec.refined

  val title: Codec[Title]                    = `enum`[Title](_.value, Title.apply, Type("title"))
  val sex: Codec[Sex]                        = `enum`[Sex](_.value, Sex.apply, Type("sex"))
  val ratingCodec: Codec[Rating]             = int4.refined[RatingConstraint].imap(Rating.apply)(_.value)
  val federationIdCodec: Codec[FederationId] = text.refined[NonEmpty].imap(FederationId.apply)(_.value)
  val playerIdCodec: Codec[PlayerId]         = int4.refined[Positive].imap(PlayerId.apply)(_.value)

  val otherTitleArr: Codec[Arr[OtherTitle]] =
    Codec.array(
      _.value,
      OtherTitle(_).toRight("invalid title"),
      Type("_other_title", List(Type("other_title")))
    )

  val otherTitles: Codec[List[OtherTitle]] = otherTitleArr.opt.imap(_.fold(Nil)(_.toList))(Arr(_*).some)

  val insertPlayer: Codec[InsertPlayer] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: ratingCodec.opt *: ratingCodec.opt *: sex.opt *: int4.opt *: bool *: federationIdCodec.opt)
      .to[InsertPlayer]

  val newFederation: Codec[NewFederation] =
    (federationIdCodec *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (federationIdCodec *: text).to[FederationInfo]

  val stats: Codec[Stats] =
    (int4 *: int4 *: int4).to[Stats]

  val federationSummary: Codec[FederationSummary] =
    (federationIdCodec *: text *: int4 *: stats *: stats *: stats).to[FederationSummary]

  val playerInfo: Codec[PlayerInfo] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: ratingCodec.opt *: ratingCodec.opt *: sex.opt *: int4.opt *: bool *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import Codecs.*

  val upsertPlayer: Command[InsertPlayer] =
    sql"""
        $insertIntoPlayer
        VALUES ($insertPlayer)
        $onPlayerConflictDoUpdate
       """.command

  // TODO use returning
  def upsertPlayers(n: Int): Command[List[InsertPlayer]] =
    val players = insertPlayer.values.list(n)
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

  def playersByIds(n: Int): Fragment[List[Int]] =
    val ids = int4.values.list(n)
    sql"$allPlayersFragment WHERE p.id IN ($ids)"

  def allFederationsSummary(paging: Pagination): AppliedFragment =
    allFederationsSummaryFragment(Void) |+| pagingFragment(paging)

  lazy val federationSummaryById: Query[FederationId, FederationSummary] =
    sql"""$allFederationsSummaryFragment
        WHERE id = $federationIdCodec""".query(federationSummary)

  private val void: AppliedFragment  = sql"".apply(Void)
  private val and: AppliedFragment   = sql"AND ".apply(Void)
  private val where: AppliedFragment = sql"WHERE ".apply(Void)

  private def between(column: String, range: RatingRange): Option[AppliedFragment] =
    val _column = s"p.$column"
    (range.min, range.max) match
      case (Some(min), Some(max)) =>
        sql"""
            #$_column BETWEEN ${int4} AND ${int4}""".apply(min, max).some
      case (Some(min), None) =>
        sql"""
            #$_column >= ${int4}""".apply(min).some
      case (None, Some(max)) =>
        sql"""
            #$_column <= ${int4}""".apply(max).some
      case (None, None) => none

  private def filterFragment(filter: PlayerFilter): Option[AppliedFragment] =
    List(
      filter.name.map(nameLikeFragment),
      between("standard", filter.standard),
      between("rapid", filter.rapid),
      between("blitz", filter.blitz),
      filter.isActive.map(filterActive),
      filter.federationId.map(federationIdFragment),
      filter.titles.map(xs => playersByTitles(xs.size)(xs, xs))
    ).flatten.match
      case Nil => none
      case xs  => xs.intercalate(and).some

  private lazy val filterActive: Fragment[Boolean] =
    sql"p.active = $bool"

  def playersByTitles(n: Int): Fragment[(List[Title], List[Title])] =
    val titles = title.values.list(n)
    sql"(p.title IN ($titles) OR p.women_title in ($titles))"

  private lazy val insertIntoPlayer =
    sql"INSERT INTO players (id, name, title, women_title, other_titles, standard, rapid, blitz, sex, birth_year, active, federation_id)"

  private lazy val onPlayerConflictDoUpdate =
    sql"""
        ON CONFLICT (id) DO UPDATE SET (name, title, women_title, other_titles, standard, rapid, blitz, sex, birth_year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.women_title, EXCLUDED.other_titles, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.sex, EXCLUDED.birth_year, EXCLUDED.active, EXCLUDED.federation_id)
      """

  private val onConflictDoNothing  = sql"ON CONFLICT DO NOTHING"
  private val insertIntoFederation = sql"INSERT INTO federations (id, name)"

  private def nameLikeFragment(name: String): AppliedFragment =
    sql"""
        p.name % $text""".apply(name)

  private def pagingFragment(page: Pagination): AppliedFragment =
    sql"""
        LIMIT ${int4} OFFSET ${int4}""".apply(page.size, page.offset)

  private def federationIdFragment(id: FederationId): AppliedFragment =
    sql"""p.federation_id = $federationIdCodec""".apply(id)

  private def sortingFragment(sorting: Sorting): AppliedFragment =
    val column  = s"p.${sorting.sortBy.value}"
    val orderBy = sorting.orderBy.value
    sql"""
        ORDER BY #$column #$orderBy NULLS LAST""".apply(Void)

  private lazy val allPlayersFragment: Fragment[Void] =
    sql"""
        SELECT p.id, p.name, p.title, p.women_title, p.other_titles, p.standard, p.rapid, p.blitz, p.sex, p.birth_year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p LEFT JOIN federations AS f ON p.federation_id = f.id
      """

  private lazy val allFederationsSummaryFragment: Fragment[Void] =
    sql"""
        SELECT id, name, players, avg_top_standard_rank, standard_players, coalesce(avg_top_standard, 0), avg_top_rapid_rank, rapid_players, coalesce(avg_top_rapid, 0), avg_top_blitz_rank, blitz_players, coalesce(avg_top_blitz, 0)
        FROM federations_summary"""
