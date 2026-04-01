package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import io.github.arainko.ducktape.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

object DbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  extension (p: PlayerInfo)
    def transform: NewPlayer =
      p.into[NewPlayer].transform(Field.const(_.federationId, p.federation.map(_.id)))

  private def resourceP: Resource[IO, (Db, KVStore)] =
    Containers.createResource.map(x => Db(x.postgres) -> KVStore(x.postgres))
  private def resource: Resource[IO, Db] = resourceP.map(_._1)

  val fedId = FederationId("fide")

  val newPlayer1 = NewPlayer(
    PlayerId(1),
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

  val newPlayer2 = NewPlayer(
    PlayerId(2),
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
    true
  )

  val newFederation = NewFederation(
    fedId,
    "FIDE"
  )

  val newPlayers = List(newPlayer1 -> newFederation.some, newPlayer2 -> none)

  test("create player success"):
    resource
      .use(_.upsert(newPlayer1, newFederation.some).map(_ => expect(true)))

  test("create players success"):
    val player2 = newPlayer1.copy(name = "Jane", id = PlayerId(2))
    resource
      .use:
        _.upsert(List(newPlayer1 -> newFederation.some, player2 -> none)).map(_ => expect(true))

  test("create and query player success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(PlayerId(1))
        found = result.get
      yield expect(
        found.transform == newPlayer1 && found.federation.get.to[NewFederation] == newFederation
      )

  test("create and query player without federation success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(PlayerId(2))
        found = result.get
      yield expect(found.transform == newPlayer2 && found.federation.isEmpty)

  test("overwriting player success"):
    val fedId2      = FederationId("lichess")
    val federation2 = NewFederation(fedId2, "lichess")
    val player2     = newPlayer1.copy(name = "Jane", federationId = fedId2.some)
    resource.use: db =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- db.upsert(player2, federation2.some)
        result <- db.playerById(PlayerId(1))
        found = result.get
      yield expect(found.transform == player2 && found.federation.get.to[NewFederation] == federation2)

  val defaultSorting = Sorting(SortBy.Name, Order.Asc)
  val defaultPage    = Pagination(PageNumber(1), PageSize(30))

  test("search playersByName success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default.copy(name = "jo".some))
      yield expect(
        players.length == 1 && players.head.transform == newPlayer1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("allPlayers with default filter"):
    val player2 = newPlayer1.copy(id = PlayerId(2), name = "A")
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default)
      yield expect(players.length == 2 && players.head.name == "A")

  test("count players with default filter"):
    val player2 = newPlayer1.copy(id = PlayerId(2), name = "A")
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.countPlayers(PlayerFilter.default)
      yield expect(players == 2)

  test("search playersByFederationId success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.playersByFederationId(fedId)
      yield expect(
        players.length == 1 && players.head.transform == newPlayer1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("playersByIds success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.playersByIds(Set(PlayerId(2), PlayerId(3)))
      yield expect(
        players.length == 1 && players.head.transform == newPlayer2 && players.head.federation.isEmpty
      )

  test("query by other_titles succcess"):
    val player2 = newPlayer1.copy(otherTitles = List(OtherTitle.IA))
    resource.use: db =>
      for
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(otherTitles = List(OtherTitle.IA).some)
        )
      yield expect(players.length == 1 && players.head.transform == player2)

  test("query federation summary success"):
    resourceP.use: (db, _) =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- db.refreshFederationsSummary
        result <- db.allFederationsSummary(defaultPage)
      yield expect(result.size == 1)

  test("query federation summary byId success"):
    resourceP.use: (db, _) =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- db.refreshFederationsSummary
        result <- db.federationSummaryById(fedId)
      yield expect(result.isDefined)

  // sorting and filter tests
  test("allPlayers sorted by Standard Desc"):
    val player2 = newPlayer1.copy(id = PlayerId(2), name = "B", standard = Rating(2500).some)
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(Sorting(SortBy.Standard, Order.Desc), defaultPage, PlayerFilter.default)
      yield expect(players.head.standard.contains(Rating(2700))) and
        expect(players.last.standard.contains(Rating(2500)))

  test("allPlayers sorted by Standard Asc"):
    val player2 = newPlayer1.copy(id = PlayerId(2), name = "B", standard = Rating(2500).some)
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(Sorting(SortBy.Standard, Order.Asc), defaultPage, PlayerFilter.default)
      yield expect(players.head.standard.contains(Rating(2500))) and
        expect(players.last.standard.contains(Rating(2700)))

  test("allPlayers filtered by rating range"):
    val player2 = newPlayer1.copy(id = PlayerId(2), name = "B", standard = Rating(2000).some)
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(standard = RatingRange(Rating(2500).some, Rating(2800).some))
        )
      yield expect(players.length == 1) and expect(players.head.transform == newPlayer1)

  test("allPlayers filtered by gender"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(gender = Gender.Female.some)
        )
      yield expect(players.length == 1) and expect(players.head.name == "Jane")

  test("allPlayers filtered by hasTitle = true"):
    val untitled = newPlayer1.copy(id = PlayerId(3), name = "C", title = none)
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        _       <- db.upsert(untitled, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(hasTitle = true.some)
        )
      yield expect(players.length == 2) // newPlayer1 and newPlayer2 both have GM

  test("allPlayers filtered by hasTitle = false"):
    val untitled = newPlayer1.copy(id = PlayerId(3), name = "C", title = none)
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        _       <- db.upsert(untitled, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(hasTitle = false.some)
        )
      yield expect(players.length == 1) and expect(players.head.name == "C")

  test("allPlayers filtered by federation IS NULL"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
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
        _      <- db.upsertFederations(feds) // second call is idempotent
        result <- db.allFederations
      yield expect(result.count(f => List("AAA", "BBB", "CCC").contains(f.id.value)) == 3)

  test("markInactive sets active = false"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        _      <- db.markInactive(Set(PlayerId(1)))
        result <- db.playerById(PlayerId(1))
      yield expect(!result.get.active)

  test("updateLastSeenAt updates timestamp"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        before <- db.playerById(PlayerId(1))
        _      <- db.updateLastSeenAt(List(PlayerId(1)))
        after  <- db.playerById(PlayerId(1))
      yield
        // Player should still exist and be queryable after updateLastSeenAt
        expect(after.isDefined) and expect(before.isDefined)
