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

object DbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resourceP: Resource[IO, (Db, KVStore)] =
    Containers.createResource.map(x => Db(x.postgres) -> KVStore(x.postgres))
  private def resource: Resource[IO, Db] = resourceP.map(_._1)

  val fedId   = FederationId("fide")
  val jan2024 = YearMonth(2024, 1)

  val crawlPlayer1 = CrawlPlayer(
    FideId("100001"),
    "John",
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.FI, OtherTitle.LSI),
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Gender.Male.some,
    1990.some,
    true,
    fedId.some
  )

  val crawlPlayer2 = CrawlPlayer(
    FideId("100002"),
    "Jane",
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.IA, OtherTitle.DI),
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Gender.Female.some,
    1990.some,
    true,
    none
  )

  val newFederation = NewFederation(fedId, "FIDE")

  def upsertOne(db: Db, player: CrawlPlayer, fed: Option[NewFederation] = none): IO[Unit] =
    val hash = CrawlPlayer.computeHash(player)
    db.upsertCrawlPlayers(List((player, fed, hash)), jan2024)

  def upsertMany(db: Db, xs: List[(CrawlPlayer, Option[NewFederation])]): IO[Unit] =
    val withHash = xs.map((p, f) => (p, f, CrawlPlayer.computeHash(p)))
    db.upsertCrawlPlayers(withHash, jan2024)

  test("create player success"):
    resource.use: db =>
      upsertOne(db, crawlPlayer1, newFederation.some).map(_ => expect(true))

  test("create players success"):
    resource.use: db =>
      upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        .map(_ => expect(true))

  test("create and query player success"):
    resource.use: db =>
      for
        _      <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        result <- db.playerById(PlayerId(1))
      yield expect(result.isDefined) and
        expect(result.get.name == "John") and
        expect(result.get.federation.get.name == "FIDE")

  test("create and query player without federation success"):
    resource.use: db =>
      for
        _ <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        // Player2 gets PlayerId(2) from sequence
        result <- db.playerById(PlayerId(2))
      yield expect(result.isDefined) and
        expect(result.get.name == "Jane") and
        expect(result.get.federation.isEmpty)

  test("overwriting player success"):
    val updated = crawlPlayer1.copy(name = "Jane Updated")
    resource.use: db =>
      for
        _      <- upsertOne(db, crawlPlayer1, newFederation.some)
        _      <- upsertOne(db, updated, newFederation.some)
        result <- db.playerById(PlayerId(1))
      yield expect(result.get.name == "Jane Updated")

  val defaultSorting = Sorting(SortBy.Name, Order.Asc)
  val defaultPage    = Pagination(PageNumber(1), PageSize(30))

  test("search playersByName success"):
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default.copy(name = "jo".some))
      yield expect(players.length == 1) and expect(players.head.name == "John")

  test("allPlayers with default filter"):
    val player2 = crawlPlayer1.copy(fideId = FideId("100099"), name = "A")
    resource.use: db =>
      for
        _       <- upsertOne(db, crawlPlayer1, newFederation.some)
        _       <- upsertOne(db, player2, newFederation.some)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default)
      yield expect(players.length == 2) and expect(players.head.name == "A")

  test("count players with default filter"):
    val player2 = crawlPlayer1.copy(fideId = FideId("100099"), name = "A")
    resource.use: db =>
      for
        _     <- upsertOne(db, crawlPlayer1, newFederation.some)
        _     <- upsertOne(db, player2, newFederation.some)
        count <- db.countPlayers(PlayerFilter.default)
      yield expect(count == 2)

  test("search playersByFederationId success"):
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        players <- db.playersByFederationId(fedId)
      yield expect(players.length == 1) and expect(players.head.name == "John")

  test("allPlayers sorted by Standard Desc"):
    val player2 = crawlPlayer1.copy(fideId = FideId("100099"), name = "B", standard = Rating(2500).some)
    resource.use: db =>
      for
        _       <- upsertOne(db, crawlPlayer1, newFederation.some)
        _       <- upsertOne(db, player2, newFederation.some)
        players <- db.allPlayers(Sorting(SortBy.Standard, Order.Desc), defaultPage, PlayerFilter.default)
      yield expect(players.head.standard.contains(Rating(2700))) and
        expect(players.last.standard.contains(Rating(2500)))

  test("allPlayers sorted by Standard Asc"):
    val player2 = crawlPlayer1.copy(fideId = FideId("100099"), name = "B", standard = Rating(2500).some)
    resource.use: db =>
      for
        _       <- upsertOne(db, crawlPlayer1, newFederation.some)
        _       <- upsertOne(db, player2, newFederation.some)
        players <- db.allPlayers(Sorting(SortBy.Standard, Order.Asc), defaultPage, PlayerFilter.default)
      yield expect(players.head.standard.contains(Rating(2500))) and
        expect(players.last.standard.contains(Rating(2700)))

  test("allPlayers filtered by rating range"):
    val player2 = crawlPlayer1.copy(fideId = FideId("100099"), name = "B", standard = Rating(2000).some)
    resource.use: db =>
      for
        _       <- upsertOne(db, crawlPlayer1, newFederation.some)
        _       <- upsertOne(db, player2, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(standard = RatingRange(Rating(2500).some, Rating(2800).some))
        )
      yield expect(players.length == 1) and expect(players.head.name == "John")

  test("allPlayers filtered by gender"):
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(gender = Gender.Female.some)
        )
      yield expect(players.length == 1) and expect(players.head.name == "Jane")

  test("allPlayers filtered by hasTitle = true"):
    val untitled = crawlPlayer1.copy(fideId = FideId("100099"), name = "C", title = none)
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        _       <- upsertOne(db, untitled, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(hasTitle = true.some)
        )
      yield expect(players.length == 2)

  test("allPlayers filtered by hasTitle = false"):
    val untitled = crawlPlayer1.copy(fideId = FideId("100099"), name = "C", title = none)
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        _       <- upsertOne(db, untitled, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(hasTitle = false.some)
        )
      yield expect(players.length == 1) and expect(players.head.name == "C")

  test("allPlayers filtered by federation IS NULL"):
    resource.use: db =>
      for
        _       <- upsertMany(db, List(crawlPlayer1 -> newFederation.some, crawlPlayer2 -> none))
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(federationId = FederationId("NON").some)
        )
      yield expect(players.length == 1) and expect(players.head.name == "Jane")

  test("upsertFederations batch is idempotent"):
    val feds = List(
      NewFederation(FederationId("AAA"), "Alpha"),
      NewFederation(FederationId("BBB"), "Beta"),
      NewFederation(FederationId("CCC"), "Charlie")
    )
    resource.use: db =>
      for
        _      <- db.upsertFederations(feds)
        _      <- db.upsertFederations(feds)
        result <- db.allFederations
      yield expect(result.count(f => List("AAA", "BBB", "CCC").contains(f.id.value)) == 3)

  test("query federation summary success"):
    resourceP.use: (db, _) =>
      for
        _      <- upsertOne(db, crawlPlayer1, newFederation.some)
        _      <- db.refreshFederationsSummary
        result <- db.allFederationsSummary(defaultPage)
      yield expect(result.size == 1)

  test("query federation summary byId success"):
    resourceP.use: (db, _) =>
      for
        _      <- upsertOne(db, crawlPlayer1, newFederation.some)
        _      <- db.refreshFederationsSummary
        result <- db.federationSummaryById(fedId)
      yield expect(result.isDefined)

  test("query by other_titles success"):
    val player = crawlPlayer1.copy(otherTitles = List(OtherTitle.IA))
    resource.use: db =>
      for
        _       <- upsertOne(db, player, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(otherTitles = List(OtherTitle.IA).some)
        )
      yield expect(players.length == 1)
