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

  val testMonth: Short = Month.current

  extension (p: PlayerInfo)
    def toNewPlayerInfo: NewPlayerInfo =
      NewPlayerInfo(p.id, p.name, p.gender, p.birthYear)
    def toNewPlayerHistory: NewPlayerHistory =
      NewPlayerHistory(
        playerId = p.id,
        month = testMonth,
        title = p.title,
        womenTitle = p.womenTitle,
        otherTitles = p.otherTitles,
        federationId = p.federation.map(_.id),
        active = p.active,
        standard = p.standard,
        standardK = p.standardK,
        rapid = p.rapid,
        rapidK = p.rapidK,
        blitz = p.blitz,
        blitzK = p.blitzK
      )

  private def resourceP: Resource[IO, (Db, KVStore)] =
    Containers.createResource.map(x => Db(x.postgres) -> KVStore(x.postgres))
  private def resource: Resource[IO, Db] = resourceP.map(_._1)

  val fedId = FederationId("fide")

  val newInfo1 = NewPlayerInfo(
    PlayerId(1),
    "John",
    Gender.Male.some,
    1990.some
  )

  val newHistory1 = NewPlayerHistory(
    PlayerId(1),
    testMonth,
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.FI, OtherTitle.LSI),
    fedId.some,
    true,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some
  )

  val newInfo2 = NewPlayerInfo(
    PlayerId(2),
    "Jane",
    Gender.Female.some,
    1990.some
  )

  val newHistory2 = NewPlayerHistory(
    PlayerId(2),
    testMonth,
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.IA, OtherTitle.DI),
    None,
    true,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some,
    Rating(2700).some,
    40.some
  )

  val newFederation = NewFederation(
    fedId,
    "FIDE"
  )

  val newPlayers = List(
    (newInfo1, newHistory1, newFederation.some),
    (newInfo2, newHistory2, none)
  )

  test("create player success"):
    resource
      .use(_.upsert(newInfo1, newHistory1, newFederation.some).map(_ => expect(true)))

  test("create players success"):
    val info2    = newInfo1.copy(name = "Jane", id = PlayerId(2))
    val history2 = newHistory1.copy(playerId = PlayerId(2), federationId = none)
    resource
      .use:
        _.upsert(List((newInfo1, newHistory1, newFederation.some), (info2, history2, none))).map(_ => expect(true))

  test("create and query player success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(PlayerId(1))
        found = result.get
      yield expect(
        found.toNewPlayerInfo == newInfo1 && found.toNewPlayerHistory == newHistory1 &&
          found.federation.get.to[NewFederation] == newFederation
      )

  test("create and query player without federation success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(PlayerId(2))
        found = result.get
      yield expect(found.toNewPlayerInfo == newInfo2 && found.toNewPlayerHistory == newHistory2 && found.federation.isEmpty)

  test("overwriting player success"):
    val fedId2      = FederationId("lichess")
    val federation2 = NewFederation(fedId2, "lichess")
    val info2       = newInfo1.copy(name = "Jane")
    val history2    = newHistory1.copy(federationId = fedId2.some)
    resource.use: db =>
      for
        _      <- db.upsert(newInfo1, newHistory1, newFederation.some)
        _      <- db.upsert(info2, history2, federation2.some)
        result <- db.playerById(PlayerId(1))
        found = result.get
      yield expect(found.toNewPlayerInfo == info2 && found.toNewPlayerHistory == history2 &&
        found.federation.get.to[NewFederation] == federation2)

  val defaultSorting = Sorting(SortBy.Name, Order.Asc)
  val defaultPage    = Pagination(PageNumber(1), PageSize(30))

  test("search playersByName success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default.copy(name = "jo".some))
      yield expect(
        players.length == 1 && players.head.toNewPlayerInfo == newInfo1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("allPlayers with default filter"):
    val info2    = newInfo1.copy(id = PlayerId(2), name = "A")
    val history2 = newHistory1.copy(playerId = PlayerId(2))
    resource.use: db =>
      for
        _       <- db.upsert(newInfo1, newHistory1, newFederation.some)
        _       <- db.upsert(info2, history2, newFederation.some)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default)
      yield expect(players.length == 2 && players.head.name == "A")

  test("count players with default filter"):
    val info2    = newInfo1.copy(id = PlayerId(2), name = "A")
    val history2 = newHistory1.copy(playerId = PlayerId(2))
    resource.use: db =>
      for
        _       <- db.upsert(newInfo1, newHistory1, newFederation.some)
        _       <- db.upsert(info2, history2, newFederation.some)
        players <- db.countPlayers(PlayerFilter.default)
      yield expect(players == 2)

  test("search playersByFederationId success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.playersByFederationId(fedId)
      yield expect(
        players.length == 1 && players.head.toNewPlayerInfo == newInfo1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("playersByIds success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayers)
        players <- db.playersByIds(Set(PlayerId(2), PlayerId(3)))
      yield expect(
        players.length == 1 && players.head.toNewPlayerInfo == newInfo2 && players.head.federation.isEmpty
      )

  test("query by other_titles succcess"):
    val history2 = newHistory1.copy(otherTitles = List(OtherTitle.IA))
    resource.use: db =>
      for
        _       <- db.upsert(newInfo1, history2, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(otherTitles = List(OtherTitle.IA).some)
        )
      yield expect(players.length == 1 && players.head.toNewPlayerHistory == history2)

  test("query federation summary success"):
    resourceP.use: (db, kv) =>
      for
        _      <- db.upsert(newInfo1, newHistory1, newFederation.some)
        _      <- kv.put("fide_last_update_key", "2021-01-01")
        result <- db.allFederationsSummary(defaultPage)
      yield expect(result.size == 1)

  test("query federation summary byId success"):
    resourceP.use: (db, kv) =>
      for
        _      <- db.upsert(newInfo1, newHistory1, newFederation.some)
        _      <- kv.put("fide_last_update_key", "2021-01-01")
        result <- db.federationSummaryById(fedId)
      yield expect(result.isDefined)
