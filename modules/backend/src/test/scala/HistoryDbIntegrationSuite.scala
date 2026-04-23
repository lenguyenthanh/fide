package fide
package test

import cats.Show
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.db.test.Containers
import fide.db.{ Db, HistoryDb }
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*
import weaver.scalacheck.Checkers

import Arbitraries.given

/** Property-based SQL smoke tests for `HistoryDb.allPlayers` and `HistoryDb.countPlayers`: every generated
  * `Sorting` / `Pagination` / `PlayerFilter` must execute without raising. A column rename in a migration
  * that isn't reflected in the SQL (see issue #112 / PR #115) would surface as a Postgres error here.
  *
  * Data is hand-seeded — no FIDE crawl — with enough variety that every `FilterSql` branch has rows that can
  * match.
  */
object HistoryDbIntegrationSuite extends IOSuite with Checkers:

  given Logger[IO] = NoOpLogger[IO]

  override type Res = (HistoryDb, YearMonth)

  override def sharedResource: Resource[IO, Res] =
    for
      dbRes <- Containers.createResource
      db        = Db(dbRes.postgres)
      historyDb = HistoryDb(dbRes.postgres, 100)
      _ <- seed(db, historyDb).toResource
    yield (historyDb, seedMonth)

  given Show[Sorting]      = Show.fromToString
  given Show[Pagination]   = Show.fromToString
  given Show[PlayerFilter] = Show.fromToString

  test("HistoryDb.allPlayers executes for every sort/filter/paging combination"):
    case (historyDb, month) =>
      forall: (sorting: Sorting, paging: Pagination, filter: PlayerFilter) =>
        historyDb
          .allPlayers(month, sorting, paging, filter)
          .map: players =>
            expect(players.size <= paging.size.value)

  test("HistoryDb.countPlayers executes for every filter, and allPlayers.size <= count"):
    case (historyDb, month) =>
      forall: (sorting: Sorting, paging: Pagination, filter: PlayerFilter) =>
        for
          players <- historyDb.allPlayers(month, sorting, paging, filter)
          count   <- historyDb.countPlayers(month, filter)
        yield expect(players.size.toLong <= count)

  private val seedMonth = YearMonth(2024, 1)
  private val prevMonth = YearMonth(2023, 12)

  private val feds = List(
    NewFederation(FederationId("USA"), "United States"),
    NewFederation(FederationId("FRA"), "France"),
    NewFederation(FederationId("GER"), "Germany")
  )

  // Varied across every dimension FilterSql touches so no branch trivially returns empty.
  private val infos: List[PlayerInfoRow] = List(
    PlayerInfoRow(PlayerId(1), FideId("100001").some, "Alice", Gender.Female.some, 1990.some),
    PlayerInfoRow(PlayerId(2), FideId("100002").some, "Bob", Gender.Male.some, 1985.some),
    PlayerInfoRow(PlayerId(3), FideId("100003").some, "Carol", Gender.Female.some, 2000.some),
    PlayerInfoRow(PlayerId(4), FideId("100004").some, "Dan", Gender.Male.some, none),
    PlayerInfoRow(PlayerId(5), FideId("100005").some, "Eve", none, 1975.some),
    PlayerInfoRow(PlayerId(6), FideId("100006").some, "Frank", Gender.Male.some, 1950.some),
    PlayerInfoRow(PlayerId(7), FideId("100007").some, "Grace", Gender.Female.some, 2005.some),
    PlayerInfoRow(PlayerId(8), none, "Harry", Gender.Male.some, 1995.some)
  )

  private def history(
      id: Int,
      month: YearMonth,
      title: Option[Title],
      womenTitle: Option[Title],
      otherTitles: List[OtherTitle],
      rating: Int,
      fed: Option[FederationId],
      active: Boolean
  ): PlayerHistoryRow =
    PlayerHistoryRow(
      playerId = PlayerId.applyUnsafe(id),
      fideId = FideId.applyUnsafe(s"10000$id").some,
      yearMonth = month,
      title = title,
      womenTitle = womenTitle,
      otherTitles = otherTitles,
      standard = Rating.applyUnsafe(rating).some,
      standardK = none,
      rapid = Rating.applyUnsafe(rating - 20).some,
      rapidK = none,
      blitz = Rating.applyUnsafe(rating - 40).some,
      blitzK = none,
      federationId = fed,
      active = active
    )

  private def histories(month: YearMonth): List[PlayerHistoryRow] =
    val usa = FederationId("USA").some
    val fra = FederationId("FRA").some
    List(
      history(1, month, Title.GM.some, Title.WGM.some, List(OtherTitle.IA), 2700, usa, active = true),
      history(2, month, Title.IM.some, none, List(OtherTitle.FA), 2500, usa, active = true),
      history(3, month, none, Title.WIM.some, Nil, 2300, fra, active = true),
      history(4, month, Title.FM.some, none, Nil, 2100, none, active = false),
      history(5, month, none, none, List(OtherTitle.IO, OtherTitle.FT), 1900, fra, active = true),
      history(6, month, Title.CM.some, none, Nil, 1700, FederationId("GER").some, active = true),
      history(7, month, none, Title.WCM.some, List(OtherTitle.DI), 1550, usa, active = false),
      history(8, month, none, none, Nil, 1480, none, active = true)
    )

  private def seed(db: Db, historyDb: HistoryDb): IO[Unit] =
    for
      _ <- db.upsertFederations(feds)
      _ <- historyDb.insertPlayerInfo(infos)
      _ <- historyDb.upsertPlayerHistory(histories(seedMonth))
      _ <- historyDb.upsertPlayerHistory(histories(prevMonth))
    yield ()
