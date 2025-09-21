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
        _ <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(
          defaultSorting,
          defaultPage,
          PlayerFilter.default.copy(otherTitles = List(OtherTitle.IA).some)
        )
      yield expect(players.length == 1 && players.head.transform == player2)

  test("query federation summary success"):
    resourceP.use: (db, kv) =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- kv.put("fide_last_update_key", "2021-01-01")
        result <- db.allFederationsSummary(defaultPage)
      yield expect(result.size == 1)

  test("query federation summary byId success"):
    resourceP.use: (db, kv) =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- kv.put("fide_last_update_key", "2021-01-01")
        result <- db.federationSummaryById(fedId)
      yield expect(result.isDefined)

  test("rating history recording and retrieval"):
    resource.use: db =>
      for
        // First, upsert a player (this should automatically record rating history)
        _             <- db.upsert(newPlayer1, newFederation.some)
        history1      <- db.ratingHistoryForPlayer(PlayerId(1))
        
        // Update player with different ratings
        updatedPlayer = newPlayer1.copy(
          standard = Rating(2750).some,
          rapid = Rating(2720).some,
          blitz = Rating(2680).some
        )
        _             <- db.upsert(updatedPlayer, None)
        history2      <- db.ratingHistoryForPlayer(PlayerId(1))
        
        // Test with limit
        historyLimited <- db.ratingHistoryForPlayer(PlayerId(1), Some(1))
      yield expect.all(
        history1.length == 1,
        history1.head.playerId == PlayerId(1),
        history1.head.standard.contains(Rating(2700)),
        history1.head.rapid.contains(Rating(2700)),
        history1.head.blitz.contains(Rating(2700)),
        history2.length == 2,
        historyLimited.length == 1,
        // Most recent entry should be first due to ordering
        historyLimited.head.standard.contains(Rating(2750))
      )

  test("rating history manual entry"):
    resource.use: db =>
      for
        _             <- db.upsert(newPlayer1, newFederation.some)
        manualEntry   = NewRatingHistoryEntry(
          playerId = PlayerId(1),
          standard = Rating(2800).some,
          rapid = Rating(2750).some,
          blitz = Rating(2700).some
        )
        _             <- db.addRatingHistory(manualEntry)
        history       <- db.ratingHistoryForPlayer(PlayerId(1))
      yield expect(
        history.length == 2 && // One from upsert, one manual
        history.exists(_.standard.contains(Rating(2800)))
      )
