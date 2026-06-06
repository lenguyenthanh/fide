package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

object HistoryDbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, HistoryDb] =
    Containers.createResource.map(x => HistoryDb(x.postgres, 100))

  val fedId   = FederationId("USA")
  val jan2024 = YearMonth(2024, 1)
  val feb2024 = YearMonth(2024, 2)

  val playerInfo1 = PlayerInfoRow(PlayerId(1), FideId("100001").some, "Alice", Gender.Female.some, 1990.some)
  val playerInfo2 = PlayerInfoRow(PlayerId(2), FideId("100002").some, "Bob", Gender.Male.some, 1985.some)

  def mkHistory(playerId: Int, month: YearMonth, rating: Int = 2700, active: Boolean = true) =
    PlayerHistoryRow(
      playerId = PlayerId.applyUnsafe(playerId),
      fideId = FideId.applyUnsafe(s"10000$playerId").some,
      yearMonth = month,
      title = Title.GM.some,
      womenTitle = none,
      otherTitles = Nil,
      standard = Rating.applyUnsafe(rating).some,
      standardK = none,
      rapid = Rating.applyUnsafe(rating).some,
      rapidK = none,
      blitz = Rating.applyUnsafe(rating).some,
      blitzK = none,
      federationId = fedId.some,
      active = active
    )

  private def resourceWithFeds: Resource[IO, (HistoryDb, Db)] =
    Containers.createResource.map(x => (HistoryDb(x.postgres, 100), Db(x.postgres)))

  private def setupFedAndInfo(db: Db, historyDb: HistoryDb, infos: List[PlayerInfoRow]): IO[Unit] =
    val fed = NewFederation(fedId, "United States")
    db.upsertFederations(List(fed)) *> historyDb.insertPlayerInfo(infos)

  test("upsertPlayerInfo is idempotent"):
    resource.use: historyDb =>
      for
        _ <- historyDb.insertPlayerInfo(List(playerInfo1))
        _ <- historyDb.insertPlayerInfo(List(playerInfo1.copy(name = "Alice Updated")))
      yield success

  test("upsertPlayerHistory with ON CONFLICT updates"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _ <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _ <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024, 2700)))
        _ <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024, 2750)))
      yield success

  test("allPlayers returns players for specific month"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _          <- setupFedAndInfo(db, historyDb, List(playerInfo1, playerInfo2))
        _          <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024), mkHistory(2, jan2024)))
        _          <- historyDb.upsertPlayerHistory(List(mkHistory(1, feb2024)))
        janPlayers <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
        febPlayers <- historyDb.allPlayers(
          feb2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
      yield expect(janPlayers.size == 2) and
        expect(janPlayers.head.name == "Alice") and
        expect(janPlayers.head.federation.isDefined) and
        expect(febPlayers.size == 1)

  test("countPlayers returns correct count for month"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _     <- setupFedAndInfo(db, historyDb, List(playerInfo1, playerInfo2))
        _     <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024), mkHistory(2, jan2024)))
        count <- historyDb.countPlayers(jan2024, PlayerFilter.default)
      yield expect(count == 2L)

  test("playerById returns player for specific month"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _       <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _       <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024, 2700)))
        result  <- historyDb.playerById(PlayerId(1), jan2024)
        missing <- historyDb.playerById(PlayerId(1), feb2024)
      yield expect(result.isDefined) and
        expect(result.get.standard.contains(Rating(2700))) and
        expect(result.get.yearMonth == jan2024) and
        expect(missing.isEmpty)

  test("allPlayers with filter"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _        <- setupFedAndInfo(db, historyDb, List(playerInfo1, playerInfo2))
        _        <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024), mkHistory(2, jan2024)))
        filtered <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default.copy(name = "ali".some)
        )
      yield expect(filtered.size == 1) and expect(filtered.head.name == "Alice")

  test("availableMonths returns distinct sorted months"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _      <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _      <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024), mkHistory(1, feb2024)))
        months <- historyDb.availableMonths
      yield expect(months.size == 2) and
        expect(months.head == feb2024) and
        expect(months.last == jan2024)

  test("federationsSummary computes on the fly"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _         <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _         <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024, 2700)))
        summaries <- historyDb.federationsSummary(jan2024, Pagination(PageNumber(1), PageSize(30)))
        count     <- historyDb.countFederationsSummary(jan2024)
      yield expect(summaries.size == 1) and
        expect(summaries.head.id == fedId) and
        expect(summaries.head.nbPlayers == 1) and
        expect(count == 1L)

  test("federationSummaryById returns specific federation"):
    resourceWithFeds.use: (historyDb, db) =>
      for
        _       <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _       <- historyDb.upsertPlayerHistory(List(mkHistory(1, jan2024, 2700)))
        result  <- historyDb.federationSummaryById(fedId, jan2024)
        missing <- historyDb.federationSummaryById(FederationId("XXX"), jan2024)
      yield expect(result.isDefined) and
        expect(result.get.nbPlayers == 1) and
        expect(missing.isEmpty)

  test("playerRatingHistory returns entries in DESC order"):
    val mar2024 = YearMonth(2024, 3)
    resourceWithFeds.use: (historyDb, db) =>
      for
        _ <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _ <- historyDb.upsertPlayerHistory(
          List(mkHistory(1, jan2024, 2700), mkHistory(1, feb2024, 2710), mkHistory(1, mar2024, 2720))
        )
        all <- historyDb.playerRatingHistory(
          PlayerId(1),
          none,
          none,
          Pagination(PageNumber(1), PageSize(30))
        )
      yield expect(all.size == 3) and
        expect(all.head.yearMonth == mar2024) and
        expect(all.last.yearMonth == jan2024)

  test("playerRatingHistory with since/until range"):
    val mar2024 = YearMonth(2024, 3)
    resourceWithFeds.use: (historyDb, db) =>
      for
        _ <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _ <- historyDb.upsertPlayerHistory(
          List(mkHistory(1, jan2024, 2700), mkHistory(1, feb2024, 2710), mkHistory(1, mar2024, 2720))
        )
        filtered <- historyDb.playerRatingHistory(
          PlayerId(1),
          jan2024.some,
          feb2024.some,
          Pagination(PageNumber(1), PageSize(30))
        )
      yield expect(filtered.size == 2) and
        expect(filtered.head.yearMonth == feb2024) and
        expect(filtered.last.yearMonth == jan2024)

  test("playerRatingHistory with pagination"):
    val mar2024 = YearMonth(2024, 3)
    resourceWithFeds.use: (historyDb, db) =>
      for
        _ <- setupFedAndInfo(db, historyDb, List(playerInfo1))
        _ <- historyDb.upsertPlayerHistory(
          List(mkHistory(1, jan2024, 2700), mkHistory(1, feb2024, 2710), mkHistory(1, mar2024, 2720))
        )
        page1 <- historyDb.playerRatingHistory(
          PlayerId(1),
          none,
          none,
          Pagination(PageNumber(1), PageSize(2))
        )
        page2 <- historyDb.playerRatingHistory(
          PlayerId(1),
          none,
          none,
          Pagination(PageNumber(2), PageSize(2))
        )
      yield expect(page1.size == 2) and
        expect(page2.size == 1) and
        expect(page1.head.yearMonth == mar2024) and
        expect(page2.head.yearMonth == jan2024)
