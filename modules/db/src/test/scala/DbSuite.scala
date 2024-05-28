package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

object DbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resourceP: Resource[IO, (Db, KVStore)] =
    Containers.createResource.map(x => Db(x.postgres) -> KVStore(x.postgres))
  private def resource: Resource[IO, Db] = resourceP.map(_._1)

  val newPlayer1 = NewPlayer(
    1,
    "John",
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.FI, OtherTitle.LSI),
    2700.some,
    2700.some,
    2700.some,
    Sex.Male.some,
    1990.some,
    true
  )

  val newPlayer2 = NewPlayer(
    2,
    "Jane",
    Title.GM.some,
    Title.WGM.some,
    List(OtherTitle.IA, OtherTitle.DI),
    2700.some,
    2700.some,
    2700.some,
    Sex.Female.some,
    1990.some,
    true
  )

  val newFederation = NewFederation(
    "fide",
    "FIDE"
  )

  val newPlayers = List(newPlayer1 -> newFederation.some, newPlayer2 -> none)

  test("create player success"):
    resource
      .use(_.upsert(newPlayer1, newFederation.some).map(_ => expect(true)))

  test("create players success"):
    val player2 = newPlayer1.copy(name = "Jane", id = 2)
    resource
      .use:
        _.upsert(List(newPlayer1 -> newFederation.some, player2 -> none)).map(_ => expect(true))

  test("create and query player success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(1)
        found = result.get
      yield expect(
        found.to[NewPlayer] == newPlayer1 && found.federation.get.to[NewFederation] == newFederation
      )

  test("create and query player without federation success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayers)
        result <- db.playerById(2)
        found = result.get
      yield expect(
        found.to[NewPlayer] == newPlayer2 && found.federation.isEmpty
      )

  test("overwriting player success"):
    val player2     = newPlayer1.copy(name = "Jane")
    val federation2 = NewFederation("Lichess", "lichess")
    resource.use: db =>
      for
        _      <- db.upsert(newPlayer1, newFederation.some)
        _      <- db.upsert(player2, federation2.some)
        result <- db.playerById(1)
        found = result.get
      yield expect(
        found.to[NewPlayer] == player2 && found.federation.get.to[NewFederation] == federation2
      )

  val defaultSorting = Sorting(SortBy.Name, Order.Asc)
  val defaultPage    = Pagination(10, 0)
  test("search playersByName success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        players <- db.playersByName("Jo", defaultSorting, defaultPage, PlayerFilter.default)
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("allPlayers sortBy name success"):
    val player2 = newPlayer1.copy(id = 2, name = "A")
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(defaultSorting, defaultPage, PlayerFilter.default)
      yield expect(players.length == 2 && players.head.name == "A")

  test("search playersByFederationId success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        players <- db.playersByFederationId("fide")
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("search playersByIds success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer1, newFederation.some)
        players <- db.playersByIds(Set(1, 2))
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer1 && players.head.federation.get
          .to[NewFederation] == newFederation
      )

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
        result <- db.federationSummaryById("fide")
      yield expect(result.isDefined)
